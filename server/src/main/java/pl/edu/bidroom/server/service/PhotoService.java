package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.persistence.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static pl.edu.bidroom.common.BusinessException.require;

/** Bounded, authenticated binary transfer over the same framed TCP/TLS connection. */
public final class PhotoService {
    public static final int MAX_BYTES=2*1024*1024,CHUNK=16384;
    public record Begin(UUID auctionId,long expectedVersion,int bytes,String sha256) { }
    private record Commit(UUID auctionId,long expectedVersion,String sha256) { }
    private static final class Upload {
        final Long user;final Begin input;final Instant expires;final ByteArrayOutputStream data=new ByteArrayOutputStream();final ReentrantLock lock=new ReentrantLock();
        Upload(Long user,Begin input,Instant expires) {this.user=user;this.input=input;this.expires=expires;}
    }
    private final ConcurrentHashMap<UUID,Upload> uploads=new ConcurrentHashMap<>();private final Semaphore slots=new Semaphore(8),decoders=new Semaphore(2);
    private final Database db;private final AuthService auth;private final AuctionService auctions;private final CommandService commands;private final Clock clock;
    public PhotoService(Database db,AuthService auth,AuctionService auctions,CommandService commands,Clock clock) {this.db=db;this.auth=auth;this.auctions=auctions;this.commands=commands;this.clock=clock;}
    public Object begin(User user,Begin input) throws Exception {
        require(input.auctionId()!=null&&input.bytes()>0&&input.bytes()<=MAX_BYTES&&input.sha256()!=null&&input.sha256().matches("[a-f0-9]{64}"),"VALIDATION_ERROR","Zdjęcie: JPG lub PNG, maksymalnie 2 MB.");
        db.read(c->{editable(auctions.repository().get(c,input.auctionId(),false),user,input.expectedVersion());return null;});
        sweep();require(slots.tryAcquire(),"CAPACITY_REACHED","Wysyłanie zdjęć zajęte. Spróbuj za chwilę.");
        UUID id=UUID.randomUUID();uploads.put(id,new Upload(user.id(),input,clock.instant().plusSeconds(300)));return Map.of("uploadId",id,"chunkBytes",CHUNK);
    }
    public Object append(User user,UUID id,long offset,String base64) {
        Upload upload=get(user,id);require(base64!=null&&base64.length()<=22000,"BAD_REQUEST","Nieprawidłowy fragment zdjęcia.");byte[] bytes;
        try {bytes=Base64.getDecoder().decode(base64);}catch(IllegalArgumentException e) {throw new BusinessException("BAD_REQUEST","Nieprawidłowe kodowanie zdjęcia.");}
        upload.lock.lock();try {
            require(bytes.length>0&&bytes.length<=CHUNK&&offset==upload.data.size()&&offset+bytes.length<=upload.input.bytes(),"BAD_REQUEST","Nieprawidłowa pozycja zdjęcia.");
            upload.data.writeBytes(bytes);return Map.of("nextOffset",upload.data.size());
        }finally {upload.lock.unlock();}
    }
    public Wire finish(User user,UUID request,UUID id) throws Exception {
        Upload upload=get(user,id);upload.lock.lockInterruptibly();
        try {
            require(upload.data.size()==upload.input.bytes(),"BAD_REQUEST","Zdjęcie jest niepełne.");
            byte[] original=upload.data.toByteArray();require(sha(original).equals(upload.input.sha256()),"BAD_REQUEST","Suma kontrolna zdjęcia nie zgadza się.");
            require(decoders.tryAcquire(),"CAPACITY_REACHED","Przetwarzanie zdjęć zajęte.");byte[] normalized;int width,height;
            try {
                BufferedImage image=decode(original);width=image.getWidth();height=image.getHeight();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"jpg",out);normalized=out.toByteArray();
            }finally {decoders.release();}
            require(normalized.length<=MAX_BYTES,"VALIDATION_ERROR","Zdjęcie jest za duże po przetworzeniu.");
            Commit input=new Commit(upload.input.auctionId(),upload.input.expectedVersion(),upload.input.sha256());
            try(var lease=auctions.runtimes().acquire(input.auctionId())) {
                var outcome=db.transaction(c->{
                    User active=auth.active(c,user.id(),true);
                    return commands.execute(c,active,request,"PHOTO_FINISH",input,()->{
                        Auction a=auctions.repository().get(c,input.auctionId(),true);editable(a,active,input.expectedVersion());
                        Sql.update(c,"INSERT INTO auction_photo VALUES(?,?,?,?,?,?,?) ON CONFLICT(auction_id) DO UPDATE SET version=excluded.version,content=excluded.content,sha256=excluded.sha256,width=excluded.width,height=excluded.height,created_at=excluded.created_at",a.auctionId(),a.photoVersion()+1,normalized,sha(normalized),width,height,clock.instant());
                        Sql.update(c,"UPDATE auction SET photo_version=photo_version+1,version=version+1,updated_at=? WHERE id=?",clock.instant(),a.auctionId());
                        Auction changed=auctions.repository().get(c,a.auctionId(),false);auctions.repository().event(c,changed,"PHOTO_SAVED",active.id(),clock.instant(),Map.of("bytes",normalized.length));return changed;
                    });
                });
                return outcome.response();
            }
        }finally {upload.lock.unlock();}
    }
    private static BufferedImage decode(byte[] bytes) throws IOException {
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);require(readers.hasNext(),"VALIDATION_ERROR","Plik nie jest zdjęciem JPG ani PNG.");var reader=readers.next();
            try {
                String format=reader.getFormatName();require(format.equalsIgnoreCase("JPEG")||format.equalsIgnoreCase("PNG"),"VALIDATION_ERROR","Wybierz JPG lub PNG.");reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);require(width>0&&height>0&&(long)width*height<=16_000_000L,"VALIDATION_ERROR","Maksymalna rozdzielczość zdjęcia: 16 megapikseli.");
                BufferedImage original=reader.read(0);double scale=Math.min(1,1280.0/Math.max(width,height));
                BufferedImage result=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=result.createGraphics();try {graphics.setColor(Color.WHITE);graphics.fillRect(0,0,result.getWidth(),result.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(original,0,0,result.getWidth(),result.getHeight(),null);}finally {graphics.dispose();}return result;
            }finally {reader.dispose();}
        }
    }
    public Object info(User user,UUID auction) throws Exception {
        return db.read(c->{Auction a=auctions.repository().get(c,auction,false);AuctionRepository.visible(a,user);
            Object result=Sql.one(c,"SELECT version,octet_length(content),sha256,width,height FROM auction_photo WHERE auction_id=?",r->Map.of("available",true,"version",r.getLong(1),"bytes",r.getInt(2),"sha256",r.getString(3),"width",r.getInt(4),"height",r.getInt(5)),auction);
            return result==null?Map.of("available",false):result;});
    }
    public Object chunk(User user,UUID auction,long version,long offset) throws Exception {
        require(offset>=0&&offset<=MAX_BYTES,"BAD_REQUEST","Nieprawidłowa pozycja zdjęcia.");
        return db.read(c->{Auction a=auctions.repository().get(c,auction,false);AuctionRepository.visible(a,user);require(a.photoVersion()==version,"VERSION_CONFLICT","Zdjęcie zostało zmienione.");
            var result=Sql.one(c,"SELECT substring(content FROM ?::integer FOR ?),octet_length(content) FROM auction_photo WHERE auction_id=? AND version=?",r->{byte[] bytes=r.getBytes(1);int size=r.getInt(2);require(offset<=size,"BAD_REQUEST","Nieprawidłowa pozycja zdjęcia.");return Map.of("base64",Base64.getEncoder().encodeToString(bytes),"nextOffset",offset+bytes.length,"eof",offset+bytes.length==size);},offset+1,CHUNK,auction,version);
            require(result!=null,"NOT_FOUND","Nie ma zdjęcia tej aukcji.");return result;});
    }
    private void editable(Auction a,User user,long version) { require(user.admin()||a.ownerId().equals(user.id()),"FORBIDDEN","Nie możesz zmieniać zdjęcia tej aukcji.");require(a.status()==Status.DRAFT,"FORBIDDEN","Zdjęcie można zmienić w szkicu.");require(a.version()==version,"VERSION_CONFLICT","Aukcja została zmieniona. Odśwież widok."); }
    private Upload get(User user,UUID id) {Upload upload=uploads.get(id);require(upload!=null&&upload.user.equals(user.id())&&clock.instant().isBefore(upload.expires),"PHOTO_EXPIRED","Wysyłanie zdjęcia wygasło. Wybierz zdjęcie ponownie.");return upload;}
    public void abort(User user,UUID id) {Upload upload=uploads.get(id);if(upload!=null&&upload.user.equals(user.id()))remove(id,upload);}
    public void clear(Long user) {uploads.forEach((id,u)->{if(u.user.equals(user))remove(id,u);});}
    public void sweep() {uploads.forEach((id,u)->{if(!clock.instant().isBefore(u.expires))remove(id,u);});}
    private void remove(UUID id,Upload upload) {if(uploads.remove(id,upload))slots.release();}
    public static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
