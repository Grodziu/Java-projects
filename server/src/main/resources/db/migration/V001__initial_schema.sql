CREATE TABLE app_user (
 id UUID PRIMARY KEY, login_normalized VARCHAR(32) NOT NULL UNIQUE, display_name VARCHAR(50) NOT NULL,
 password_hash BYTEA NOT NULL, password_salt BYTEA NOT NULL, password_algorithm TEXT NOT NULL DEFAULT 'PBKDF2WithHmacSHA256',
 password_iterations INTEGER NOT NULL CHECK(password_iterations >= 600000), role TEXT NOT NULL CHECK(role IN ('USER','ADMIN')),
 enabled BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE auction (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES app_user(id), title VARCHAR(120) NOT NULL,
 description VARCHAR(4000) NOT NULL, category TEXT NOT NULL CHECK(category IN ('ELECTRONICS','BOOKS','ACCESSORIES','OTHER')),
 status TEXT NOT NULL CHECK(status IN ('DRAFT','SCHEDULED','ACTIVE','FINISHED','CANCELLED')),
 starting_price BIGINT NOT NULL CHECK(starting_price BETWEEN 1 AND 1000000000000),
 min_increment BIGINT NOT NULL CHECK(min_increment BETWEEN 1 AND 1000000000000),
 start_at TIMESTAMPTZ, initial_end_at TIMESTAMPTZ, end_at TIMESTAMPTZ, duration_seconds BIGINT NOT NULL CHECK(duration_seconds BETWEEN 60 AND 604800),
 current_price BIGINT CHECK(current_price BETWEEN 1 AND 1000000000000), leader_id UUID REFERENCES app_user(id),
 bid_count BIGINT NOT NULL DEFAULT 0 CHECK(bid_count >= 0), version BIGINT NOT NULL DEFAULT 1 CHECK(version > 0),
 published_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, cancel_reason VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK ((current_price IS NULL) = (leader_id IS NULL)),
 CHECK ((bid_count = 0) = (current_price IS NULL)),
 CHECK (published_at IS NULL OR (start_at IS NOT NULL AND initial_end_at > start_at AND end_at >= initial_end_at)),
 CHECK (status NOT IN ('SCHEDULED','ACTIVE','FINISHED') OR published_at IS NOT NULL)
);
CREATE INDEX auction_catalog ON auction(status,start_at,id);
CREATE INDEX auction_owner ON auction(owner_id,created_at,id);
CREATE TABLE bid (
 id UUID PRIMARY KEY, auction_id UUID NOT NULL REFERENCES auction(id), bidder_id UUID NOT NULL REFERENCES app_user(id),
 sequence_no BIGINT NOT NULL CHECK(sequence_no > 0), amount BIGINT NOT NULL CHECK(amount BETWEEN 1 AND 1000000000000),
 accepted_at TIMESTAMPTZ NOT NULL, command_request_id UUID NOT NULL,
 UNIQUE(auction_id,sequence_no), UNIQUE(bidder_id,command_request_id)
);
CREATE TABLE auction_event (
 id UUID PRIMARY KEY, auction_id UUID NOT NULL REFERENCES auction(id), auction_version BIGINT NOT NULL,
 event_type TEXT NOT NULL, actor_id UUID REFERENCES app_user(id), occurred_at TIMESTAMPTZ NOT NULL, details JSONB NOT NULL,
 UNIQUE(auction_id,auction_version,event_type)
);
CREATE INDEX auction_event_time ON auction_event(auction_id,occurred_at,id);
CREATE TABLE command_result (
 user_id UUID NOT NULL REFERENCES app_user(id), request_id UUID NOT NULL, operation TEXT NOT NULL,
 input_digest TEXT NOT NULL, result JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(user_id,request_id)
);
CREATE TABLE security_audit (
 id UUID PRIMARY KEY, actor_id UUID REFERENCES app_user(id), target_user_id UUID REFERENCES app_user(id),
 action TEXT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, details JSONB NOT NULL
);
CREATE TABLE diagnostic_log (
 id UUID PRIMARY KEY, origin TEXT NOT NULL CHECK(origin IN ('CLIENT','SERVER')), origin_instance_id UUID NOT NULL,
 event_id UUID NOT NULL, reported_by_user_id UUID REFERENCES app_user(id), client_occurred_at TIMESTAMPTZ,
 received_at TIMESTAMPTZ NOT NULL, level TEXT NOT NULL CHECK(level IN ('INFO','WARN','ERROR')),
 event_type VARCHAR(60) NOT NULL, auction_id UUID, command_request_id UUID, message VARCHAR(1000) NOT NULL,
 CHECK(origin <> 'CLIENT' OR reported_by_user_id IS NOT NULL)
);
CREATE UNIQUE INDEX client_log_dedup ON diagnostic_log(reported_by_user_id,origin_instance_id,event_id) WHERE origin='CLIENT';
CREATE UNIQUE INDEX server_log_dedup ON diagnostic_log(origin_instance_id,event_id) WHERE origin='SERVER';
CREATE INDEX diagnostic_time ON diagnostic_log(received_at,id);
CREATE INDEX diagnostic_user ON diagnostic_log(reported_by_user_id,received_at);
CREATE INDEX diagnostic_auction ON diagnostic_log(auction_id,received_at);
