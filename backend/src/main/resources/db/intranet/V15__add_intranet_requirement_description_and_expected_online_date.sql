ALTER TABLE intranet_requirement
    ADD COLUMN description VARCHAR(1000) NULL AFTER title,
    ADD COLUMN expected_online_date DATE NULL AFTER transaction_id;
