package pl.edu.bidroom.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import pl.edu.bidroom.client.*;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;

public final class MainController {
    @FXML private BorderPane root;
    @FXML private VBox sidebar,content;
    @FXML private ScrollPane scroll,sidebarPane;
    @FXML private Label screenTitle,userName,connectionLabel,message;
    @FXML private Button adminNav,logoutButton;
    private PhotoViews photos;private ClientGateway gateway;private Stage stage;private Timeline ticker;private Long visibleUser;
    private String view="login";private int generation,catalogOffset;private boolean loading,bidAwaiting;
    private boolean catalogRefreshQueued;private boolean statsLoading;private long lastStats;private VBox statsBody;private Label reportStatus;private boolean reportReady,reportLoading;private long lastReport;
    private TextField search,amount;private ComboBox<String> category;private FlowPane cards;private Button nextPage,previousPage,bidButton;
    private Auction selected;private Label price,status,time,minimum,leader,ending;
    private TableView<Bid> bids;private long bidCursor;private boolean historyLoading;
    private final Map<Label,Instant> countdowns=new HashMap<>();
    private static final List<String> CATEGORY_CODES=List.of("ELECTRONICS","BOOKS","ACCESSORIES","OTHER");
    private static final List<String> CATEGORY_NAMES=List.of("Elektronika","Nauka i książki","Akcesoria","Inne");
    private static final ZoneId ZONE=ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE);
    public void initializeGateway(ClientGateway gateway,Stage stage) {
        this.gateway=gateway;this.photos=new PhotoViews(gateway);this.stage=stage;
        sidebarPane.viewportBoundsProperty().addListener((o,old,bounds)->sidebar.setMinHeight(bounds.getHeight()));
        content.setMinWidth(0);showLogin(false);
        ticker=new Timeline(new KeyFrame(Duration.millis(250),event->tick()));ticker.setCycleCount(Timeline.INDEFINITE);ticker.play();
    }
    public void stop() { if(ticker!=null)ticker.stop();if(photos!=null)photos.close(); }
    private void tick() {
        connectionLabel.setText(gateway.status());connectionLabel.getStyleClass().removeAll("success","error");connectionLabel.getStyleClass().add(gateway.online()?"success":"error");
        User user=gateway.user();
        if(user==null&&visibleUser!=null) { visibleUser=null;showLogin(false); }
        else if(user!=null&&!user.id().equals(visibleUser)) { visibleUser=user.id();showCatalog("active"); }
        sidebarPane.setVisible(user!=null);sidebarPane.setManaged(user!=null);logoutButton.setVisible(user!=null);logoutButton.setManaged(user!=null);adminNav.setVisible(user!=null&&user.admin());adminNav.setManaged(adminNav.isVisible());userName.setVisible(user!=null);userName.setManaged(user!=null);userName.setText(user==null?"":user.displayName());if(user==null)userName.setGraphic(null);else if(userName.getGraphic()==null||!Objects.equals(userName.getUserData(),user.id())){userName.setGraphic(Visuals.avatar(user.id(),30));userName.setUserData(user.id());}
        if(view.equals("admin-stats")&&System.nanoTime()-lastStats>2_000_000_000L)refreshStats();
        if(selected!=null&&selected.status().terminal()&&!reportReady&&System.nanoTime()-lastReport>2_000_000_000L)refreshReport();
        Wire snapshot=gateway.pollSnapshot();if(snapshot!=null&&selected!=null&&selected.auctionId().toString().equals(snapshot.payload().path("auctionId").asText()))updateAuction(Json.read(snapshot.payload(),Auction.class));
        if(gateway.pollCatalogDirty()&&isCatalog()) { if(loading)catalogRefreshQueued=true;else refreshCatalog(); }
        String notice=gateway.pollNotice();if(notice!=null)message(notice,false);
        countdowns.forEach((label,end)->label.setText(remaining(end)));
        if(selected!=null&&time!=null) {
            time.setText(selected.status()==Status.SCHEDULED?"Start za "+remaining(selected.startAt()):selected.status()==Status.ACTIVE?remaining(selected.endAt()):statusName(selected.status()));
            bidButton.setDisable(bidAwaiting||!gateway.online()||user==null||selected.ownerId().equals(user.id())||selected.status()!=Status.ACTIVE);
        }
    }
    private void showLogin(boolean register) {
        generation++;view="login";selected=null;countdowns.clear();content.getChildren().clear();screenTitle.setText(register?"Załóż konto":"Witaj w BidRoom");
        BrandArtwork pattern=new BrandArtwork();pattern.setPrefHeight(150);pattern.setMinHeight(120);pattern.setMaxHeight(150);
        VBox credits=new VBox(5,label("Piotr Grodny • Marcin Gurdziel • Oliwia Jarosz","team-names"),label("Programowanie w języku Java","project-course"),label("Informatyka w inżynierii komputerowej\n5. semestr • grupa P1 • rok 2026/2027","project-meta"));credits.setId("projectCredits");
        VBox hero=new VBox(12,label("BIDROOM", "eyebrow-light"),label("Każda oferta\nma znaczenie.","hero-title"),label("Licytuj w punktach. Obserwuj zmiany na żywo.\nTwórz własne aukcje i sprawdzaj wyniki.","hero-copy"),new Separator(),label("Projekt studencki • Politechnika Krakowska","hero-copy"),pattern,credits);hero.getStyleClass().add("hero");hero.setMinWidth(0);hero.setMaxWidth(Double.MAX_VALUE);
        TextField login=field("np. anna.nowak");login.setId("login");PasswordField password=new PasswordField();password.setId("password");password.setPromptText("Co najmniej 12 znaków");TextField display=field("Jak mają widzieć Cię inni?");PasswordField repeated=new PasswordField();
        VBox form=new VBox(14,label(register?"Dołącz do licytacji":"Zaloguj się","card-title"),label("Jedno konto do wystawiania i licytowania.","subtitle"),labelled("Login",login));
        if(register)form.getChildren().add(labelled("Nazwa wyświetlana",display));TextField plain=new TextField();plain.textProperty().bindBidirectional(password.textProperty());plain.setManaged(false);plain.setVisible(false);CheckBox reveal=new CheckBox("Pokaż hasło");reveal.setOnAction(e->{plain.setVisible(reveal.isSelected());plain.setManaged(reveal.isSelected());password.setVisible(!reveal.isSelected());password.setManaged(!reveal.isSelected());});form.getChildren().addAll(labelled("Hasło",new VBox(password,plain)),reveal);if(register)form.getChildren().add(labelled("Powtórz hasło",repeated));
        Button submit=button(register?"Utwórz konto":"Zaloguj się",null);submit.setId("loginSubmit");submit.setDefaultButton(true);submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(e->{
            String loginValue=login.getText(),passwordValue=password.getText(),displayValue=display.getText();
            if(register&&!passwordValue.equals(repeated.getText())) { message("Hasła muszą być jednakowe.",true);return; }
            run(submit,()-> { if(register)gateway.register(loginValue,displayValue,passwordValue);return gateway.login(loginValue,passwordValue); },u->{password.clear();repeated.clear();visibleUser=u.id();message("Połączono. Wybierz aukcję lub utwórz własną.",false);showCatalog("active");});
        });
        Button change=button(register?"Mam już konto":"Nie masz konta? Zarejestruj się",()->showLogin(!register));change.getStyleClass().add("secondary");change.setMaxWidth(Double.MAX_VALUE);
        form.getChildren().addAll(submit,change,label("Połączenie jest konfigurowane automatycznie.","subtitle"));form.getStyleClass().addAll("card","login-form");form.setMinWidth(0);form.setMaxWidth(Double.MAX_VALUE);
        GridPane columns=new GridPane();columns.setId("loginColumns");columns.setHgap(24);columns.setVgap(20);columns.setMaxWidth(1040);
        Consumer<Number> arrange=width->{boolean wide=width.doubleValue()>=700;columns.getChildren().clear();columns.getColumnConstraints().clear();ColumnConstraints first=new ColumnConstraints();first.setPercentWidth(wide?54:100);columns.getColumnConstraints().add(first);if(wide){ColumnConstraints second=new ColumnConstraints();second.setPercentWidth(46);columns.getColumnConstraints().add(second);columns.add(hero,0,0);columns.add(form,1,0);}else{columns.add(form,0,0);columns.add(hero,0,1);}};
        columns.widthProperty().addListener((o,old,width)->{if((old.doubleValue()>=700)!=(width.doubleValue()>=700))arrange.accept(width);});arrange.accept(content.getWidth()-48);StackPane centered=new StackPane(columns);content.getChildren().add(centered);scroll.setVvalue(0);
    }
    @FXML private void activeCatalog(){showCatalog("active");}@FXML private void scheduledCatalog(){showCatalog("scheduled");}@FXML private void myCatalog(){showCatalog("mine");}@FXML private void historyCatalog(){showCatalog("history");}
    @FXML private void retry(){gateway.retry();message("Ponawianie połączenia…",false);}
    @FXML private void logout(){run(logoutButton,()->{gateway.logout();return true;},x->{visibleUser=null;showLogin(false);});}
    private boolean isCatalog(){return Set.of("active","scheduled","mine","history").contains(view);}
    private void showCatalog(String kind) {
        if(gateway.user()==null)return;generation++;view=kind;selected=null;catalogOffset=0;loading=false;countdowns.clear();content.getChildren().clear();
        screenTitle.setText(switch(kind){case "mine"->"Moje aukcje";case "history"->"Historia aukcji";case "scheduled"->"Zaplanowane aukcje";default->"Aukcje na żywo";});
        Label intro=label(kind.equals("mine")?"Od pomysłu do ostatniej oferty. Zarządzaj swoimi aukcjami.":"Wybierz przedmiot i dołącz do licytacji w wirtualnych punktach.","subtitle");
        search=field("Szukaj po tytule…");search.setPrefWidth(250);category=new ComboBox<>();category.getItems().add("Wszystkie kategorie");category.getItems().addAll(CATEGORY_NAMES);category.getSelectionModel().select(0);
        Button find=button("Szukaj",()->{catalogOffset=0;refreshCatalog();});search.setOnAction(e->find.fire());category.setOnAction(e->{catalogOffset=0;refreshCatalog();});
        Button create=button("+ Nowa aukcja",()->draftDialog(null));FlowPane filters=new FlowPane(12,12,search,category,find,create);
        cards=new FlowPane(16,16);cards.setPrefWrapLength(850);cards.prefWrapLengthProperty().bind(content.widthProperty().subtract(56));
        previousPage=button("← Poprzednia",()->{catalogOffset=Math.max(0,catalogOffset-12);refreshCatalog();});nextPage=button("Następna →",()->refreshCatalog());
        BrandArtwork art=new BrandArtwork();art.setPrefSize(200,112);art.setMaxSize(200,112);art.setMinWidth(120);VBox welcome=new VBox(6,label("Twoja przestrzeń licytacji","banner-title"),intro);welcome.setMinWidth(0);welcome.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(welcome,Priority.ALWAYS);HBox banner=new HBox(16,welcome,art);banner.setMinWidth(0);banner.getStyleClass().add("catalog-banner");content.getChildren().addAll(banner,filters,cards,new HBox(12,previousPage,nextPage));scroll.setVvalue(0);refreshCatalog();
    }
    private void refreshCatalog() {
        if(!isCatalog()||gateway.user()==null)return;if(loading){catalogRefreshQueued=true;return;}loading=true;int stamp=generation,offset=catalogOffset;String query=search.getText(),kind=view;int catIndex=category.getSelectionModel().getSelectedIndex();String cat=catIndex<=0?"":CATEGORY_CODES.get(catIndex-1);
        run(null,()->gateway.request("LIST_AUCTIONS",Map.of("view",kind,"query",query,"category",cat,"offset",offset,"pageSize",12)),result->{
            if(stamp!=generation)return;loading=false;cards.getChildren().clear();countdowns.clear();
            for(JsonNode item:result.path("items"))cards.getChildren().add(auctionCard(Json.read(item,Auction.class)));
            if(cards.getChildren().isEmpty())cards.getChildren().add(label("Nie ma aukcji spełniających wybrane kryteria.","subtitle"));
            previousPage.setDisable(offset==0);nextPage.setDisable(!result.path("hasMore").asBoolean());int next=result.path("nextOffset").asInt();nextPage.setOnAction(e->{catalogOffset=next;refreshCatalog();});
            if(catalogRefreshQueued) { catalogRefreshQueued=false;refreshCatalog(); }
        },()->{if(stamp==generation)loading=false;});
    }
    private Node auctionCard(Auction a) {
        Label price=label(points(a.currentPrice()==null?a.startingPrice():a.currentPrice()),"price");Label remaining=label(statusName(a.status()),"subtitle");
        if(a.status()==Status.ACTIVE)countdowns.put(remaining,a.endAt());else if(a.status()==Status.SCHEDULED)remaining.setText("Start: "+date(a.startAt()));
        Label title=label(a.title(),"card-title");title.setWrapText(true);title.setMinHeight(52);
        VBox box=new VBox(12,photos.create(a,265,158),label("#"+a.auctionNumber()+" • "+categoryName(a.category())+" • "+statusName(a.status()),"eyebrow"),title,price,remaining,Visuals.person(a.ownerId(),a.ownerName()+" • "+a.bidCount()+" ofert"),button("Otwórz aukcję →",()->openAuction(a.auctionId())));
        box.setId("auction-"+a.auctionId());box.setPrefWidth(315);box.setMinWidth(270);box.getStyleClass().addAll("card","category-"+a.category());return box;
    }
    private void openAuction(UUID id) {
        int stamp=++generation;view="detail";selected=null;countdowns.clear();message("Pobieranie aktualnego stanu…",false);
        run(null,()->gateway.subscribe(id),a->{if(stamp!=generation)return;renderDetail(a);message("Czas jest orientacyjny; o przyjęciu oferty decyduje serwer.",false);});
    }
    private void renderDetail(Auction a) {
        selected=a;content.getChildren().clear();screenTitle.setText("Aukcja #"+a.auctionNumber());historyLoading=false;bidCursor=0;
        Label title=label(a.title(),"card-title"),description=label(a.description(),"subtitle");description.setWrapText(true);description.setMaxWidth(900);
        status=label("","eyebrow");price=label("","price");time=label("","timer");minimum=label("","subtitle");leader=label("","subtitle");ending=label("","subtitle");
        amount=input(String.valueOf(minimum(a)));amount.setId("bidAmount");amount.setPrefWidth(180);bidButton=button("Licytuj",this::placeBid);bidButton.setId("bidSubmit");
        FlowPane bidControls=new FlowPane(12,12,amount,bidButton);
        VBox information=new VBox(12,description,Visuals.person(a.ownerId(),"Wystawia: "+a.ownerName()),leader,ending);information.setPrefWidth(330);
        FlowPane figures=new FlowPane(20,8,price,time);figures.setAlignment(Pos.CENTER_LEFT);
        VBox bidding=new VBox(12,figures,minimum,bidControls);bidding.setPrefWidth(370);
        VBox informationAndBid=new VBox(20,information,bidding);informationAndBid.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(490,content.widthProperty().subtract(106)));informationAndBid.setMinWidth(0);information.setMinWidth(0);bidding.setMinWidth(0);information.setPrefWidth(490);bidding.setPrefWidth(490);FlowPane details=new FlowPane(24,20,photos.create(a,265,240),informationAndBid);
        VBox summary=new VBox(18,status,title,details);summary.getStyleClass().addAll("card","category-"+a.category());
        bids=new TableView<>();bids.setPrefHeight(260);bids.setFixedCellSize(54);bids.setPlaceholder(label("Nie przyjęto jeszcze żadnej oferty.","subtitle"));
        column(bids,"Nr",b->""+b.sequence(),55);personColumn(bids);column(bids,"Oferta",b->points(b.amount()),150);column(bids,"Czas (Europe/Warsaw)",b->date(b.acceptedAt()),220);
        Button historyFirst=button("Początek historii",()->{bidCursor=0;loadBids();});Button historyNext=button("Dalsze oferty →",this::loadBids);
        FlowPane actions=new FlowPane(12,12);User user=gateway.user();boolean manage=user!=null&&(user.admin()||user.id().equals(a.ownerId()));
        if(a.status()==Status.DRAFT&&manage) { actions.getChildren().add(button("Edytuj szkic",()->draftDialog(selected)));actions.getChildren().add(button(a.photoVersion()==0?"Dodaj zdjęcie":"Zmień zdjęcie",()->choosePhoto(selected)));actions.getChildren().add(button("Opublikuj",()->publish(selected))); }
        if(!a.status().terminal()&&manage) { Button cancel=button("Anuluj aukcję",()->cancel(selected));cancel.getStyleClass().add("danger");actions.getChildren().add(cancel); }
        Button export=button("Pobierz raport .txt",()->export(selected));export.setDisable(!a.status().terminal());export.setId("exportReport");actions.getChildren().add(export);
        reportStatus=label(a.status().terminal()?"Raport jest zapisywany automatycznie na serwerze…":"Raport powstanie automatycznie po zakończeniu tej aukcji.","subtitle");reportReady=false;lastReport=0;content.getChildren().addAll(summary,actions,reportStatus,label("Historia zaakceptowanych ofert","card-title"),bids,new HBox(12,historyFirst,historyNext));updateAuction(a);loadBids();
    }
    private void refreshReport() {
        if(reportLoading||selected==null||!gateway.online())return;UUID auction=selected.auctionId();reportLoading=true;lastReport=System.nanoTime();
        run(null,()->gateway.request("GET_REPORT_STATUS",Map.of("auctionId",auction)),r->{reportLoading=false;if(selected!=null&&selected.auctionId().equals(auction)&&r.path("ready").asBoolean()){reportReady=true;reportStatus.setText("Raport zapisany automatycznie: "+r.path("filename").asText());}},()->reportLoading=false);
    }
    private void updateAuction(Auction a) {
        if(selected!=null&&a.version()<selected.version())return;Auction old=selected;selected=a;
        if(old!=null&&old.status()!=a.status()) { renderDetail(a);return; }
        price.setText(points(a.currentPrice()==null?a.startingPrice():a.currentPrice()));status.setText(categoryName(a.category())+" • "+statusName(a.status()));
        minimum.setText(a.status()==Status.ACTIVE?"Minimalna oferta: "+points(minimum(a)):"Cena początkowa: "+points(a.startingPrice())+" • przebicie: "+points(a.minIncrement()));
        if(a.status()==Status.CANCELLED)leader.setText("Anulowano: "+a.cancelReason()+". Brak zwycięzcy.");
        else if(a.status()==Status.FINISHED)leader.setText(a.leaderId()==null?"Aukcja zakończona bez ofert.":"Zwycięzca: "+a.leaderName()+" • "+points(a.currentPrice()));
        else if(gateway.user()!=null&&a.ownerId().equals(gateway.user().id()))leader.setText("To Twoja aukcja — możesz obserwować, ale nie możesz jej licytować.");
        else leader.setText(a.leaderId()==null?"Pierwsza oferta może być równa cenie początkowej.":a.leaderId().equals(gateway.user().id())?"Prowadzisz w tej aukcji.":"Prowadzi: "+a.leaderName());
        ending.setText("Termin: "+date(a.endAt())+" • Europe/Warsaw • "+a.bidCount()+" ofert");
        if(old!=null&&old.endAt()!=null&&a.endAt()!=null&&a.endAt().isAfter(old.endAt()))message("Czas przedłużony. Nowy termin: "+date(a.endAt()),false);
        if(a.leaderId()!=null)leader.setGraphic(Visuals.avatar(a.leaderId(),30));
        if(old!=null&&old.bidCount()!=a.bidCount()) { if(amount.getText().equals(String.valueOf(minimum(old)))||a.leaderId().equals(gateway.user().id()))amount.setText(String.valueOf(minimum(a)));bidCursor=0;loadBids(); }
    }
    private void loadBids() {
        if(historyLoading||selected==null)return;historyLoading=true;UUID id=selected.auctionId();long cursor=bidCursor;int stamp=generation;
        run(null,()->gateway.request("GET_BIDS",Map.of("auctionId",id,"afterSequence",cursor,"limit",20)),result->{
            if(stamp!=generation||selected==null||!selected.auctionId().equals(id))return;historyLoading=false;
            if(cursor==0)bids.getItems().clear();for(JsonNode node:result.path("items")) { Bid b=Json.read(node,Bid.class);if(bids.getItems().stream().noneMatch(existing->existing.bidId().equals(b.bidId())))bids.getItems().add(b); }
            while(bids.getItems().size()>100)bids.getItems().removeFirst();bidCursor=result.path("nextSequence").asLong();
        },()->{if(stamp==generation)historyLoading=false;});
    }
    private void placeBid() {
        if(bidAwaiting||selected==null)return;long value;try{value=Long.parseLong(amount.getText().strip());}catch(Exception e){message("Wpisz całkowitą liczbę punktów.",true);return;}
        UUID id=selected.auctionId();bidAwaiting=true;message("Oferta wysłana — oczekiwanie na potwierdzenie…",false);
        run(bidButton,()->gateway.bid(id,value),receipt->{bidAwaiting=false;if(selected!=null&&selected.auctionId().equals(id))updateAuction(receipt.auction());message("Oferta przyjęta: "+points(receipt.amount())+". Zapis potwierdzony przez serwer.",false);},()->bidAwaiting=false);
    }
    private void draftDialog(Auction existing) {
        Dialog<ButtonType> dialog=dialog(existing==null?"Nowa aukcja":"Edytuj szkic");dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zapisz szkic",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);
        TextField title=input(existing==null?"":existing.title());title.setId("draftTitle");TextArea description=new TextArea(existing==null?"":existing.description());description.setId("draftDescription");description.setPrefRowCount(3);description.setWrapText(true);
        ComboBox<String> cat=new ComboBox<>();cat.getItems().addAll(CATEGORY_NAMES);cat.getSelectionModel().select(existing==null?0:CATEGORY_CODES.indexOf(existing.category()));
        TextField startPrice=input(existing==null?"100":""+existing.startingPrice()),increment=input(existing==null?"10":""+existing.minIncrement()),duration=input(existing==null?"5":new java.math.BigDecimal(existing.durationSeconds()).divide(java.math.BigDecimal.valueOf(60),2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        CheckBox now=new CheckBox("Rozpocznij od razu po publikacji");now.setSelected(existing==null||existing.startAt()==null);
        startPrice.setId("draftPrice");increment.setId("draftIncrement");duration.setId("draftDuration");
        ZonedDateTime initial=existing==null||existing.startAt()==null?ZonedDateTime.now(ZONE).plusMinutes(5):existing.startAt().atZone(ZONE);DatePicker date=new DatePicker(initial.toLocalDate());TextField hour=input(initial.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        date.disableProperty().bind(now.selectedProperty());hour.disableProperty().bind(now.selectedProperty());
        VBox form=new VBox(12,label("Po zapisaniu szkicu dodaj zdjęcie przed publikacją.","subtitle"),labelled("Tytuł",title),labelled("Opis",description),labelled("Kategoria",cat),new FlowPane(12,12,labelled("Cena początkowa (pkt)",startPrice),labelled("Minimalne przebicie (pkt)",increment)),labelled("Czas trwania (minuty, 1–10080)",duration),now,new FlowPane(12,12,labelled("Data początku • Europe/Warsaw",date),labelled("Godzina HH:mm",hour)));
        ScrollPane pane=new ScrollPane(form);pane.setFitToWidth(true);pane.setPrefViewportHeight(490);dialog.getDialogPane().setContent(pane);dialog.getDialogPane().setPrefWidth(620);
        dialog.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{
            try {
                Instant start=null;if(!now.isSelected()) { LocalDateTime local=LocalDateTime.of(date.getValue(),LocalTime.parse(hour.getText()));var offsets=ZONE.getRules().getValidOffsets(local);if(offsets.size()!=1)throw new IllegalArgumentException("Godzina nie istnieje lub jest niejednoznaczna przez zmianę czasu. Wybierz inną.");start=local.toInstant(offsets.getFirst()); }
                long seconds=new java.math.BigDecimal(duration.getText().replace(',','.')).multiply(java.math.BigDecimal.valueOf(60)).longValueExact();
                Draft draft=new Draft(existing==null?null:existing.auctionId(),existing==null?0:existing.version(),title.getText(),description.getText(),CATEGORY_CODES.get(cat.getSelectionModel().getSelectedIndex()),Long.parseLong(startPrice.getText()),Long.parseLong(increment.getText()),start,seconds);
                run(null,()->Json.read(gateway.request(existing==null?"CREATE_DRAFT":"UPDATE_DRAFT",draft),Auction.class),saved->{message("Zapisano szkic. Sprawdź warunki przed publikacją.",false);openAuction(saved.auctionId());});
            }catch(Exception e){message(e.getMessage()==null?"Sprawdź kwoty, datę i czas trwania.":e.getMessage(),true);}
        });
    }
    private void choosePhoto(Auction a) {
        FileChooser chooser=new FileChooser();chooser.setTitle("Zdjęcie przedmiotu • JPG lub PNG do 2 MB");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zdjęcia JPG i PNG","*.jpg","*.jpeg","*.png"));File file=chooser.showOpenDialog(stage);if(file==null)return;
        run(null,()->{if(java.nio.file.Files.size(file.toPath())>2*1024*1024)throw new java.io.IOException("Wybierz zdjęcie do 2 MB. Zmniejsz rozdzielczość większego pliku.");return PhotoTransfer.upload(gateway::request,a,java.nio.file.Files.readAllBytes(file.toPath()));},saved->{renderDetail(saved);message("Zdjęcie zapisano na serwerze i przypisano do aukcji.",false);});
    }
    private void publish(Auction a) {
        if(a.photoVersion()==0){message("Najpierw dodaj zdjęcie przedmiotu.",true);choosePhoto(a);return;}
        if(!confirm("Opublikować aukcję „"+a.title()+"”?","Cena: "+points(a.startingPrice())+"\nPrzebicie: "+points(a.minIncrement())+"\nCzas: "+a.durationSeconds()+" s\nPoczątek: "+(a.startAt()==null?"od razu":date(a.startAt()))+"\nPo publikacji warunki pozostaną niezmienne."))return;
        run(null,()->Json.read(gateway.request("PUBLISH_AUCTION",new Publish(a.auctionId(),a.version())),Auction.class),saved->{renderDetail(saved);message("Aukcja została opublikowana.",false);});
    }
    private void cancel(Auction a) {
        TextInputDialog dialog=new TextInputDialog();dialog.initOwner(stage);dialog.setTitle("Anulowanie aukcji");dialog.setHeaderText("Powód anulowania: "+a.title());dialog.setContentText("Powód:");
        dialog.showAndWait().ifPresent(reason->{if(confirm("Anulować aukcję „"+a.title()+"”?",reason))run(null,()->Json.read(gateway.request("CANCEL_AUCTION",new Cancel(a.auctionId(),reason)),Auction.class),this::renderDetail);});
    }
    private void export(Auction a) {
        FileChooser chooser=new FileChooser();chooser.setTitle("Zapisz wynik aukcji");chooser.setInitialFileName("aukcja-"+a.auctionNumber()+".txt");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Raport tekstowy","*.txt"));File file=chooser.showSaveDialog(stage);
        if(file!=null)run(null,()->{gateway.export(a.auctionId(),file.toPath());return file;},saved->message("Raport zapisany i sprawdzony: "+saved.getName(),false));
    }
    @FXML private void administration() {
        if(gateway.user()==null||!gateway.user().admin())return;generation++;view="admin";selected=null;countdowns.clear();content.getChildren().clear();screenTitle.setText("Administracja");
        content.getChildren().add(new FlowPane(12,12,button("Stan serwera",this::serverStats),button("Konta",()->users(0)),button("Utwórz konto",this::createUser),button("Logi klienta i serwera",()->logs(0))));serverStats();
    }
    private void personColumn(TableView<Bid> table) {
        TableColumn<Bid,Bid> column=new TableColumn<>("Uczestnik");column.setPrefWidth(230);column.setCellValueFactory(row->new javafx.beans.property.ReadOnlyObjectWrapper<>(row.getValue()));
        column.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Bid bid,boolean empty){super.updateItem(bid,empty);setText(null);setGraphic(empty||bid==null?null:Visuals.person(bid.bidderId(),bid.bidderName()));}});table.getColumns().add(column);
    }
    private void serverStats() {generation++;view="admin-stats";statsBody=new VBox(20);adminBody(statsBody);lastStats=0;refreshStats();}
    private void refreshStats() {
        if(statsLoading||!gateway.online()||!view.equals("admin-stats"))return;statsLoading=true;lastStats=System.nanoTime();
        run(null,()->gateway.request("ADMIN_SERVER_STATS",Map.of()),r->{
            statsLoading=false;FlowPane figures=new FlowPane(14,14);
            figures.getChildren().addAll(metric("Połączenia klientów",r.path("clients")+" / "+r.path("maxClients"),0),metric("Automaty aukcji",r.path("liveAuctions")+" / "+r.path("maxAuctions"),1),metric("Wątki aplikacji",r.path("tasks").path("total").asText(),3),metric("Pamięć JVM",(r.path("heapUsedBytes").asLong()/1048576)+" / "+(r.path("heapMaxBytes").asLong()/1048576)+" MB",4),metric("CPU procesu",r.path("cpuPercent").asDouble()<0?"Pomiar…":r.path("cpuPercent").asText()+"%",2),metric("Połączenia JDBC",r.path("databaseActive")+" / "+r.path("databasePoolMax"),5));
            TableView<JsonNode> table=new TableView<>();table.setPrefHeight(270);column(table,"ID wątku",n->n.path("threadId").asText(),95);column(table,"Zadanie",n->n.path("role").asText(),110);column(table,"Opis",n->n.path("name").asText(),250);column(table,"Typ",n->n.path("virtual").asBoolean()?"Wirtualny":"Platformowy",110);column(table,"Uruchomiono",n->date(Instant.parse(n.path("startedAt").asText())),190);r.path("tasks").path("items").forEach(table.getItems()::add);
            JsonNode counts=r.path("counts");statsBody.getChildren().setAll(figures,label("Działa od "+r.path("uptimeSeconds").asLong()/60+" min • "+(r.path("tls").asBoolean()?"TCP z TLS":"Lokalne TCP")+" • odświeżanie co 2 s","subtitle"),label("Aktywne zadania aplikacji","card-title"),table,label("Rejestr pokazuje rzeczywiste wątki zadań aplikacji. Wątki platformowe całej JVM: "+r.path("platformThreads")+". Rozpoczęcie i zakończenie zadań trafia do logów.","subtitle"),label("Baza: "+counts.path("users")+" kont • "+counts.path("auctions")+" aukcji • "+counts.path("bids")+" ofert • "+counts.path("photos")+" zdjęć • "+counts.path("reports")+" raportów","subtitle"),label("Automatyczne raporty na serwerze: "+r.path("reportsPath").asText()+"\nRejestr wszystkich raportów: raport-ogolny.txt","subtitle"));
        },()->statsLoading=false);
    }
    private Node metric(String title,String value,int color) {VBox box=new VBox(8,label(title,"subtitle"),label(value,"metric-value"));box.getStyleClass().add("metric");box.setStyle("-fx-border-color: "+Visuals.COLORS[color]+"; -fx-border-width: 4 0 0 0;");box.setPrefWidth(220);return box;}
    private void adminBody(Node... nodes) { while(content.getChildren().size()>1)content.getChildren().removeLast();content.getChildren().addAll(nodes); }
    private void users(int offset) { users(offset,""); }
    private void users(int offset,String filterText) {
        generation++;view="admin-users";
        TableView<User> table=new TableView<>();table.setPrefHeight(380);column(table,"Nr",u->"#"+u.id(),60);column(table,"Login",User::login,170);column(table,"Nazwa",User::displayName,240);column(table,"Rola",u->u.admin()?"Administrator":"Użytkownik",150);column(table,"Stan",u->u.enabled()?"Aktywne":"Zablokowane",150);
        TextField query=field("Szukaj loginu…");query.setText(filterText);Button filter=button("Szukaj",()->users(0,query.getText())),previous=button("← Poprzednia",()->users(Math.max(0,offset-20),filterText)),next=button("Następna →",null);previous.setDisable(offset==0);query.setOnAction(e->filter.fire());
        Runnable load=()->run(filter,()->gateway.request("ADMIN_LIST_USERS",Map.of("query",filterText,"offset",offset,"pageSize",20)),result->{table.getItems().clear();for(JsonNode n:result.path("items"))table.getItems().add(Json.read(n,User.class));next.setDisable(!result.path("hasMore").asBoolean());next.setOnAction(e->users(result.path("nextOffset").asInt(),filterText));});
        Button toggle=button("Zablokuj / odblokuj wybrane konto",()->{User chosen=table.getSelectionModel().getSelectedItem();if(chosen==null){message("Wybierz konto.",true);return;}TextInputDialog reason=new TextInputDialog();reason.initOwner(stage);reason.setHeaderText((chosen.enabled()?"Blokada: ":"Odblokowanie: ")+chosen.login());reason.setContentText("Powód:");reason.showAndWait().ifPresent(text->{if(confirm("Zmienić stan konta „"+chosen.login()+"”?",text))run(null,()->gateway.request("ADMIN_SET_USER_STATUS",new UserStatus(chosen.id(),!chosen.enabled(),text)),result->load.run());});});
        adminBody(new FlowPane(12,12,query,filter),table,toggle,new HBox(12,previous,next));load.run();
    }
    private void createUser() {
        Dialog<ButtonType> d=dialog("Utwórz konto");d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        TextField login=field(""),display=field("");PasswordField password=new PasswordField();ComboBox<String> role=new ComboBox<>();role.getItems().addAll("USER","ADMIN");role.getSelectionModel().select(0);
        d.getDialogPane().setContent(new VBox(12,labelled("Login",login),labelled("Nazwa wyświetlana",display),labelled("Hasło (12–128 znaków)",password),labelled("Rola",role)));
        d.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{NewUser input=new NewUser(login.getText(),display.getText(),password.getText(),role.getValue());run(null,()->gateway.request("ADMIN_CREATE_USER",input),result->{password.clear();users(0);message("Utworzono konto.",false);});});
    }
    private void logs(int offset) { logs(offset,Map.of()); }
    private void logs(int offset,Map<String,Object> filters) {
        generation++;view="admin-logs";
        ComboBox<String> origin=new ComboBox<>();origin.getItems().addAll("Wszystkie źródła","CLIENT","SERVER");origin.getSelectionModel().select(0);ComboBox<String> level=new ComboBox<>();level.getItems().addAll("Wszystkie poziomy","INFO","WARN","ERROR");level.getSelectionModel().select(0);
        TextField user=field("ID użytkownika (opcjonalne)"),auction=field("ID aukcji (opcjonalne)"),from=field("Od: UTC, np. 2026-11-10T00:00:00Z"),to=field("Do: UTC (opcjonalnie)");
        if(filters.containsKey("origin"))origin.getSelectionModel().select(filters.get("origin").toString());if(filters.containsKey("level"))level.getSelectionModel().select(filters.get("level").toString());
        user.setText(filters.getOrDefault("userId","").toString());auction.setText(filters.getOrDefault("auctionId","").toString());from.setText(filters.getOrDefault("from","").toString());to.setText(filters.getOrDefault("to","").toString());
        TableView<JsonNode> table=new TableView<>();table.setPrefHeight(400);column(table,"Otrzymano",n->n.path("receivedAt").asText(),180);column(table,"Czas klienta",n->n.path("clientOccurredAt").asText(),180);column(table,"Źródło",n->n.path("origin").asText(),85);column(table,"Poziom",n->n.path("level").asText(),85);column(table,"Zdarzenie",n->n.path("eventType").asText(),160);column(table,"Wiadomość",n->n.path("message").asText(),340);
        Button filter=button("Filtruj",null),previous=button("← Poprzednia",()->logs(Math.max(0,offset-20),filters)),next=button("Następna →",null);previous.setDisable(offset==0);
        Runnable load=()->{var args=new HashMap<>(filters);args.put("offset",offset);args.put("pageSize",20);run(filter,()->gateway.request("ADMIN_GET_LOGS",args),result->{table.getItems().clear();result.path("items").forEach(table.getItems()::add);next.setDisable(!result.path("hasMore").asBoolean());next.setOnAction(e->logs(result.path("nextOffset").asInt(),filters));});};
        filter.setOnAction(e->{var args=new HashMap<String,Object>();if(origin.getSelectionModel().getSelectedIndex()>0)args.put("origin",origin.getValue());if(level.getSelectionModel().getSelectedIndex()>0)args.put("level",level.getValue());if(!user.getText().isBlank())args.put("userId",user.getText());if(!auction.getText().isBlank())args.put("auctionId",auction.getText());if(!from.getText().isBlank())args.put("from",from.getText());if(!to.getText().isBlank())args.put("to",to.getText());logs(0,Map.copyOf(args));});
        adminBody(label("CLIENT to deklaracje klienta; decyzje aukcji są utrwalane niezależnie w audycie serwera.","subtitle"),new FlowPane(12,12,origin,level,user,auction,from,to,filter),table,new HBox(12,previous,next));load.run();
    }
    private <T> void run(Button button,Callable<T> work,Consumer<T> success) { run(button,work,success,()->{}); }
    private <T> void run(Button button,Callable<T> work,Consumer<T> success,Runnable failure) {
        int startedGeneration=generation;
        if(button!=null)button.setDisable(true);
        gateway.submit(work,(value,error)->Platform.runLater(()->{if(button!=null)button.setDisable(false);if(startedGeneration!=generation){failure.run();return;}if(error!=null){failure.run();message(error.getMessage()==null?"Nie można wykonać operacji.":error.getMessage(),true);}else success.accept(value);}));
    }
    private void message(String text,boolean error) { message.setText(text);message.getStyleClass().removeAll("error","success");if(error)message.getStyleClass().add("error"); }
    private static Label label(String text,String style) { Label label=new Label(text);label.getStyleClass().add(style);label.setWrapText(true);return label; }
    private static TextField field(String prompt) { TextField field=new TextField();field.setPromptText(prompt);return field; }
    private static TextField input(String value) { return new TextField(value); }
    private static VBox labelled(String text,Node node) { Label label=label(text,"form-label");label.setLabelFor(node);return new VBox(6,label,node); }
    private static Button button(String text,Runnable action) { Button button=new Button(text);if(action!=null)button.setOnAction(e->action.run());return button; }
    private <T> void column(TableView<T> table,String title,Function<T,String> text,int width) { TableColumn<T,String> column=new TableColumn<>(title);column.setCellValueFactory(row->new ReadOnlyStringWrapper(text.apply(row.getValue())));column.setPrefWidth(width);table.getColumns().add(column);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); }
    private Dialog<ButtonType> dialog(String title) { Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(stage);dialog.setTitle(title);dialog.setHeaderText(title);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/ui/app.css").toExternalForm());return dialog; }
    private boolean confirm(String title,String body) { Alert alert=new Alert(Alert.AlertType.CONFIRMATION,body,ButtonType.OK,ButtonType.CANCEL);alert.initOwner(stage);alert.setTitle("Potwierdzenie");alert.setHeaderText(title);return alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK; }
    private String remaining(Instant end) { if(end==null)return "—";long seconds=Math.max(0,java.time.Duration.between(gateway.estimatedNow(),end).getSeconds());return seconds==0?"Oczekiwanie na rozstrzygnięcie serwera":String.format("%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60); }
    private static String date(Instant time) { return time==null?"—":DATE.format(time); }
    private static String points(long amount) { return String.format(Locale.forLanguageTag("pl-PL"),"%,d pkt",amount); }
    private static String categoryName(String code) { int i=CATEGORY_CODES.indexOf(code);return i<0?code:CATEGORY_NAMES.get(i); }
    private static long minimum(Auction a) { return a.currentPrice()==null?a.startingPrice():Math.addExact(a.currentPrice(),a.minIncrement()); }
    private static String statusName(Status status) { return switch(status){case DRAFT->"Szkic";case SCHEDULED->"Zaplanowana";case ACTIVE->"Trwa licytacja";case FINISHED->"Zakończona";case CANCELLED->"Anulowana";}; }
}
