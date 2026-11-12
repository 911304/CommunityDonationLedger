
CREATE DATABASE IF NOT EXISTS community_donation_ledger;

USE community_donation_ledger;

CREATE TABLE IF NOT EXISTS admin (
    admin_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS donor (
    donor_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS beneficiary (
    beneficiary_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(100),
    contact VARCHAR(30),
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS donation (
    donation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    donation_date DATE NOT NULL,
    donation_type VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT fk_donation_donor
        FOREIGN KEY (donor_id) REFERENCES donor(donor_id),
    CONSTRAINT fk_donation_admin
        FOREIGN KEY (admin_id) REFERENCES admin(admin_id),
    CONSTRAINT chk_donation_amount CHECK (amount > 0)
);

CREATE TABLE IF NOT EXISTS allocation (
    allocation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donation_id BIGINT NOT NULL,
    beneficiary_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    allocation_date DATE NOT NULL,
    amount_allocated DECIMAL(12,2) NOT NULL,
    purpose VARCHAR(500),
    status ENUM('PENDING','ALLOCATED','FULFILLED','CANCELLED')
        DEFAULT 'ALLOCATED',
    CONSTRAINT fk_allocation_donation
        FOREIGN KEY (donation_id) REFERENCES donation(donation_id),
    CONSTRAINT fk_allocation_beneficiary
        FOREIGN KEY (beneficiary_id) REFERENCES beneficiary(beneficiary_id),
    CONSTRAINT fk_allocation_admin
        FOREIGN KEY (admin_id) REFERENCES admin(admin_id),
    CONSTRAINT chk_allocation_amount CHECK (amount_allocated > 0)
);

INSERT IGNORE INTO admin (admin_id, name, email, password)
VALUES (1, 'Project Administrator', 'admin@example.com', NULL);
