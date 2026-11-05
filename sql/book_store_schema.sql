CREATE DATABASE IF NOT EXISTS book_store_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE book_store_db;

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    import_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    selling_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    quantity INT NOT NULL DEFAULT 0,
    description TEXT,
    category_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign Key referencing categories table
    CONSTRAINT fk_products_category 
        FOREIGN KEY (category_id) 
        REFERENCES categories(id) 
        ON DELETE RESTRICT 
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(512) NOT NULL,
    public_id VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_product_images_product 
        FOREIGN KEY (product_id) 
        REFERENCES products(id) 
        ON DELETE CASCADE 
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_product_images_product_id ON product_images(product_id);

INSERT INTO categories (id, name, description) VALUES
(1, 'Technology', 'Books covering software engineering, programming, and IT.'),
(2, 'Science Fiction', 'Speculative fiction involving futuristic science and space travel.'),
(3, 'Self-Help', 'Books focusing on personal growth, habits, and productivity.');

INSERT INTO products (id, name, import_price, selling_price, quantity, description, category_id, created_at) VALUES
(1, 'Clean Code: A Handbook of Agile Software Craftsmanship', 28.50, 42.99, 50, 'Even bad code can function. But if code isn\'t clean, it can bring a development organization to its knees.', 1, NOW()),
(2, 'The Pragmatic Programmer: Your Journey to Mastery', 30.00, 45.00, 35, 'One of the most significant books in software development.', 1, NOW()),
(3, 'Dune (Dune Chronicles, Book 1)', 10.20, 18.99, 100, 'Set on the desert planet Arrakis, Dune is the story of Paul Atreides.', 2, NOW()),
(4, 'Atomic Habits: An Easy & Proven Way to Build Good Habits', 12.00, 21.99, 80, 'Tiny Changes, Remarkable Results.', 3, NOW()),
(5, 'Designing Data-Intensive Applications', 32.00, 49.99, 25, 'An invaluable guide for software engineers and architects.', 1, NOW());

INSERT INTO product_images (url, public_id, product_id) VALUES
('https://res.cloudinary.com/demo/image/upload/v1710000000/bookstore/products/clean_code_front.jpg', 'bookstore/products/clean_code_front', 1),
('https://res.cloudinary.com/demo/image/upload/v1710000001/bookstore/products/clean_code_back.jpg', 'bookstore/products/clean_code_back', 1),
('https://res.cloudinary.com/demo/image/upload/v1710000002/bookstore/products/pragmatic_programmer.jpg', 'bookstore/products/pragmatic_programmer', 2),
('https://res.cloudinary.com/demo/image/upload/v1710000003/bookstore/products/dune_cover.jpg', 'bookstore/products/dune_cover', 3),
('https://res.cloudinary.com/demo/image/upload/v1710000004/bookstore/products/atomic_habits_front.jpg', 'bookstore/products/atomic_habits_front', 4),
('https://res.cloudinary.com/demo/image/upload/v1710000005/bookstore/products/ddia_front.jpg', 'bookstore/products/ddia_front', 5);

ALTER TABLE products 
ADD COLUMN is_deleted TINYINT(1) DEFAULT 0,
ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL;

ALTER TABLE product_images 
ADD COLUMN is_deleted TINYINT(1) DEFAULT 0,
ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL;

ALTER TABLE categories
ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS orphaned_cloudinary_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED_PERMANENTLY, MANUALLY_RESOLVED
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, retry_count)
);

CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER', -- 'CUSTOMER' or 'ADMIN'
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE,
    address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_carts_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    order_code VARCHAR(64) UNIQUE NOT NULL,
    subtotal_amount DECIMAL(12, 2) NOT NULL,
    shipping_fee DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL,
    order_status ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_status ENUM('UNPAID', 'PAID', 'REFUNDED') NOT NULL DEFAULT 'UNPAID',
    payment_method ENUM('COD', 'BANK_TRANSFER') NOT NULL,
    shipping_full_name VARCHAR(100) NOT NULL,
    shipping_phone_number VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE products 
ADD COLUMN total_ratings INT DEFAULT 0,
ADD COLUMN rating_sum INT DEFAULT 0,
ADD COLUMN average_rating DECIMAL(3, 2) DEFAULT 0.00;

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_reviews_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

ALTER TABLE reviews
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
ADD COLUMN edit_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_products_average_rating ON products(average_rating);

ALTER TABLE product_images 
ADD COLUMN is_primary TINYINT(1) NOT NULL DEFAULT 0,
ADD COLUMN display_order INT NOT NULL DEFAULT 0;

-- Creates a conditional unique index so a product_id can only have ONE row where is_primary = 1
CREATE UNIQUE INDEX idx_product_primary_image 
ON product_images (product_id, (CASE WHEN is_primary = 1 THEN 1 ELSE NULL END));

START TRANSACTION;

INSERT IGNORE INTO categories (name, description) VALUES
  ('Fantasy', 'Epic quests, magic and imaginary worlds.'),
  ('Mystery & Thriller', 'Detectives, crime, suspense and page-turning twists.'),
  ('History & Biography', 'True stories, historical accounts and the lives of remarkable people.');

SET @fantasy = (SELECT id FROM categories WHERE name = 'Fantasy');
SET @mystery = (SELECT id FROM categories WHERE name = 'Mystery & Thriller');
SET @history = (SELECT id FROM categories WHERE name = 'History & Biography');

INSERT INTO products (name, import_price, selling_price, quantity, description, category_id) VALUES
('The Hobbit', 10.25, 14.99, 70, 'By J.R.R. Tolkien. Bilbo Baggins leaves his comfortable hobbit hole to help a band of dwarves reclaim their treasure from a dragon.', @fantasy),
('The Fellowship of the Ring', 12.97, 18.99, 147, 'By J.R.R. Tolkien. The first volume of The Lord of the Rings: Frodo inherits a dangerous ring and sets out to destroy it.', @fantasy),
('The Two Towers', 8.18, 13.99, 31, 'By J.R.R. Tolkien. The Fellowship is broken and the war for Middle-earth grows as Frodo and Sam head toward Mordor.', @fantasy),
('The Return of the King', 11.71, 17.99, 14, 'By J.R.R. Tolkien. The final volume of The Lord of the Rings, where the fate of Middle-earth is decided.', @fantasy),
('A Game of Thrones', 19.72, 28.99, 147, 'By George R.R. Martin. Noble families fight for the Iron Throne while an ancient threat gathers beyond the Wall.', @fantasy),
('A Clash of Kings', 16.07, 24.99, 79, 'By George R.R. Martin. The Seven Kingdoms are torn apart by rival claimants to the throne.', @fantasy),
('A Storm of Swords', 8.13, 11.99, 3, 'By George R.R. Martin. Alliances shatter and shocking betrayals reshape the war for Westeros.', @fantasy),
('Harry Potter and the Philosopher''s Stone', 12.20, 19.99, 94, 'By J.K. Rowling. A boy discovers he is a wizard and begins his first year at Hogwarts.', @fantasy),
('Harry Potter and the Chamber of Secrets', 9.49, 14.99, 99, 'By J.K. Rowling. In his second year, Harry faces a hidden chamber and a monster that petrifies students.', @fantasy),
('Harry Potter and the Prisoner of Azkaban', 15.59, 22.49, 125, 'By J.K. Rowling. A dangerous prisoner escapes from Azkaban and Harry learns secrets about his past.', @fantasy),
('The Name of the Wind', 20.76, 28.99, 104, 'By Patrick Rothfuss. Kvothe, a legend living quietly as an innkeeper, tells the story of his own life.', @fantasy),
('The Wise Man''s Fear', 9.39, 13.49, 100, 'By Patrick Rothfuss. The second day of Kvothe''s story takes him to new lands and tougher trials.', @fantasy),
('Mistborn: The Final Empire', 20.35, 29.99, 19, 'By Brandon Sanderson. A crew of thieves plans to overthrow an immortal ruler in a world of ash and metal-fuelled magic.', @fantasy),
('The Way of Kings', 13.27, 18.49, 67, 'By Brandon Sanderson. The epic first book of The Stormlight Archive, set in a world of storms and ancient oaths.', @fantasy),
('A Wizard of Earthsea', 9.28, 14.99, 101, 'By Ursula K. Le Guin. A young wizard named Ged learns the cost of power after releasing a shadow into the world.', @fantasy),
('The Lion, the Witch and the Wardrobe', 10.38, 16.49, 76, 'By C.S. Lewis. Four siblings step through a wardrobe into the magical land of Narnia.', @fantasy),
('American Gods', 9.16, 13.99, 70, 'By Neil Gaiman. Shadow Moon is drawn into a war between old gods and new ones across America.', @fantasy),
('Good Omens', 10.76, 16.99, 150, 'By Terry Pratchett and Neil Gaiman. An angel and a demon team up to stop the apocalypse in this comic fantasy.', @fantasy),
('The Colour of Magic', 12.91, 18.49, 22, 'By Terry Pratchett. The first Discworld novel, following a hapless wizard and a naive tourist.', @fantasy),
('The Eye of the World', 13.15, 18.99, 1, 'By Robert Jordan. The first book of The Wheel of Time: three young men leave their village pursued by dark forces.', @fantasy),
('The Blade Itself', 12.18, 19.99, 88, 'By Joe Abercrombie. A gritty fantasy following a torturer, a barbarian and a vain young nobleman.', @fantasy),
('The Lies of Locke Lamora', 11.43, 17.99, 125, 'By Scott Lynch. A band of clever thieves schemes its way through a canal city.', @fantasy),
('Assassin''s Apprentice', 9.29, 15.49, 145, 'By Robin Hobb. A royal bastard is trained as an assassin in the Six Duchies.', @fantasy),
('The Last Wish', 14.11, 19.99, 110, 'By Andrzej Sapkowski. Short stories introducing Geralt of Rivia, the witcher.', @fantasy),
('Stardust', 16.54, 22.99, 43, 'By Neil Gaiman. A young man crosses a wall into the land of Faerie to retrieve a fallen star.', @fantasy),
('Coraline', 16.59, 27.99, 20, 'By Neil Gaiman. A girl finds a door to another house where everything seems better, but is dangerously wrong.', @fantasy),
('The Princess Bride', 10.01, 14.99, 116, 'By William Goldman. A tale of true love, pirates, giants and swordplay.', @fantasy),
('Eragon', 8.86, 13.99, 127, 'By Christopher Paolini. A farm boy finds a dragon egg and is drawn into a rebellion.', @fantasy),
('The Golden Compass', 19.68, 27.49, 10, 'By Philip Pullman. Lyra Belacqua travels north to rescue kidnapped children in the first book of His Dark Materials.', @fantasy),
('A Court of Thorns and Roses', 9.96, 14.49, 95, 'By Sarah J. Maas. A huntress is taken to a faerie realm and drawn into a dangerous romance.', @fantasy),
('The Priory of the Orange Tree', 9.29, 14.49, 124, 'By Samantha Shannon. An epic standalone fantasy about dragons, queens and a secret order.', @fantasy),
('Jonathan Strange & Mr Norrell', 8.23, 11.49, 53, 'By Susanna Clarke. Two magicians revive English magic during the Napoleonic Wars.', @fantasy),
('Piranesi', 19.65, 27.99, 2, 'By Susanna Clarke. A man lives in a vast house of endless halls and statues and begins to uncover its mystery.', @fantasy),
('The Fifth Season', 16.83, 27.99, 49, 'By N.K. Jemisin. In a world of catastrophic upheaval, a woman searches for her kidnapped daughter.', @fantasy),
('The Girl with the Dragon Tattoo', 6.15, 9.49, 36, 'By Stieg Larsson. A journalist and a hacker investigate a decades-old disappearance in a wealthy Swedish family.', @mystery),
('Gone Girl', 12.57, 20.49, 69, 'By Gillian Flynn. On their anniversary a woman vanishes and her husband becomes the prime suspect.', @mystery),
('The Da Vinci Code', 8.18, 11.99, 25, 'By Dan Brown. A symbologist follows a trail of clues hidden in art and history after a murder at the Louvre.', @mystery),
('Angels & Demons', 9.41, 13.99, 148, 'By Dan Brown. Robert Langdon races to stop an ancient secret society''s threat against the Vatican.', @mystery),
('Murder on the Orient Express', 9.47, 14.49, 116, 'By Agatha Christie. Hercule Poirot investigates a murder aboard a snowbound train.', @mystery),
('And Then There Were None', 10.87, 15.99, 110, 'By Agatha Christie. Ten strangers are stranded on an island and are killed one by one.', @mystery),
('The Murder of Roger Ackroyd', 14.82, 20.99, 123, 'By Agatha Christie. Poirot, in retirement, investigates a murder in a quiet English village.', @mystery),
('The Hound of the Baskervilles', 7.94, 12.99, 94, 'By Arthur Conan Doyle. Sherlock Holmes investigates a legendary spectral hound haunting the moors.', @mystery),
('A Study in Scarlet', 6.62, 9.99, 9, 'By Arthur Conan Doyle. The first story in which Holmes and Dr Watson meet and solve a puzzling murder.', @mystery),
('The Big Sleep', 7.34, 11.99, 16, 'By Raymond Chandler. Private detective Philip Marlowe is hired by a dying general to deal with his troubled daughters.', @mystery),
('The Maltese Falcon', 13.03, 19.99, 79, 'By Dashiell Hammett. Sam Spade hunts for a priceless statuette in a web of lies and double-crosses.', @mystery),
('Rebecca', 16.38, 24.99, 129, 'By Daphne du Maurier. A young bride is haunted by the memory of her husband''s first wife at Manderley.', @mystery),
('The Silence of the Lambs', 11.77, 16.99, 56, 'By Thomas Harris. An FBI trainee seeks help from the imprisoned Hannibal Lecter to catch a serial killer.', @mystery),
('Big Little Lies', 8.73, 12.99, 98, 'By Liane Moriarty. Three mothers'' secrets collide at a school trivia night that ends in a death.', @mystery),
('The Girl on the Train', 14.84, 22.99, 21, 'By Paula Hawkins. A commuter becomes entangled in a missing-person case she glimpsed from her train window.', @mystery),
('In the Woods', 8.27, 12.99, 94, 'By Tana French. A detective investigates a child''s murder near the woods where he once vanished as a boy.', @mystery),
('Sharp Objects', 7.88, 12.99, 145, 'By Gillian Flynn. A reporter returns to her hometown to cover the murders of two young girls.', @mystery),
('The Secret History', 15.33, 23.99, 79, 'By Donna Tartt. A group of classics students at an elite college are bound together by a crime.', @mystery),
('Shutter Island', 16.85, 23.99, 27, 'By Dennis Lehane. A U.S. marshal investigates a patient''s disappearance from a secure psychiatric hospital.', @mystery),
('Mystic River', 14.08, 23.99, 146, 'By Dennis Lehane. A murder reunites three childhood friends whose lives were shaped by a past tragedy.', @mystery),
('The Name of the Rose', 7.09, 9.99, 68, 'By Umberto Eco. A medieval monk investigates a series of deaths in a remote abbey.', @mystery),
('The Thursday Murder Club', 9.71, 14.99, 62, 'By Richard Osman. Four retirees meet weekly to investigate cold cases and get drawn into a real murder.', @mystery),
('The Silent Patient', 13.26, 21.99, 4, 'By Alex Michaelides. A woman stops speaking after shooting her husband, and a therapist is determined to find out why.', @mystery),
('The Talented Mr. Ripley', 15.25, 21.49, 124, 'By Patricia Highsmith. A charming con man becomes entangled in a deadly deception in Italy.', @mystery),
('Strangers on a Train', 12.87, 18.99, 150, 'By Patricia Highsmith. Two strangers meet and discuss swapping murders, with deadly consequences.', @mystery),
('The Hunt for Red October', 15.16, 24.99, 63, 'By Tom Clancy. A Soviet submarine captain attempts to defect, setting off a Cold War chase.', @mystery),
('Casino Royale', 7.53, 10.99, 22, 'By Ian Fleming. James Bond''s first adventure pits him against a Soviet agent at a high-stakes card table.', @mystery),
('The Bourne Identity', 7.15, 10.99, 143, 'By Robert Ludlum. A man with no memory and extraordinary skills is hunted across Europe.', @mystery),
('Along Came a Spider', 10.71, 14.99, 28, 'By James Patterson. Detective Alex Cross hunts a kidnapper who has taken two children from a school.', @mystery),
('Killing Floor', 9.94, 14.99, 68, 'By Lee Child. Jack Reacher is arrested in a small Georgia town and uncovers a conspiracy.', @mystery),
('The Cuckoo''s Calling', 15.65, 21.99, 71, 'By Robert Galbraith. Private investigator Cormoran Strike looks into the apparent suicide of a famous model.', @mystery),
('Before I Go to Sleep', 7.02, 10.99, 141, 'By S.J. Watson. A woman who loses her memory every night pieces together the truth with the help of a journal.', @mystery),
('The Woman in White', 11.86, 19.49, 88, 'By Wilkie Collins. A Victorian mystery of mistaken identity, secrets and a mysterious figure in white.', @mystery),
('Sapiens', 13.02, 20.49, 84, 'By Yuval Noah Harari. A sweeping history of humankind from the Stone Age to the present.', @history),
('Homo Deus', 19.52, 27.49, 26, 'By Yuval Noah Harari. A look at how technology and data might shape the future of humanity.', @history),
('Guns, Germs, and Steel', 9.33, 13.99, 33, 'By Jared Diamond. Why some societies came to dominate others over the last 13,000 years.', @history),
('The Diary of a Young Girl', 10.41, 15.99, 41, 'By Anne Frank. The wartime diary of a teenager hiding from the Nazis in Amsterdam.', @history),
('Steve Jobs', 17.57, 24.99, 102, 'By Walter Isaacson. The authorized biography of Apple''s co-founder, based on dozens of interviews.', @history),
('Einstein: His Life and Universe', 14.74, 22.99, 147, 'By Walter Isaacson. A biography of the physicist, from his early years to his scientific achievements.', @history),
('Leonardo da Vinci', 15.48, 22.99, 149, 'By Walter Isaacson. A biography of the artist and inventor, drawn from his notebooks.', @history),
('Long Walk to Freedom', 16.36, 22.99, 42, 'By Nelson Mandela. The autobiography of Nelson Mandela, from his childhood to the presidency of South Africa.', @history),
('The Autobiography of Malcolm X', 15.49, 21.99, 149, 'By Malcolm X with Alex Haley. The life story of Malcolm X as told to Alex Haley.', @history),
('Educated', 10.83, 17.49, 61, 'By Tara Westover. A memoir of a woman who grew up in a survivalist family and went on to earn a PhD.', @history),
('Becoming', 16.22, 23.99, 75, 'By Michelle Obama. The memoir of the former First Lady of the United States.', @history),
('I Know Why the Caged Bird Sings', 18.45, 29.99, 21, 'By Maya Angelou. The first volume of Maya Angelou''s autobiography, about her childhood and resilience.', @history),
('The Wright Brothers', 11.13, 15.99, 19, 'By David McCullough. The story of the two brothers who achieved the first powered flight.', @history),
('John Adams', 9.28, 13.49, 75, 'By David McCullough. A biography of the second U.S. president.', @history),
('Team of Rivals', 12.48, 18.99, 117, 'By Doris Kearns Goodwin. How Abraham Lincoln built a cabinet from his political rivals.', @history),
('A People''s History of the United States', 18.46, 30.99, 46, 'By Howard Zinn. American history told from the perspective of ordinary people.', @history),
('The Rise and Fall of the Third Reich', 21.60, 30.99, 149, 'By William L. Shirer. A comprehensive history of Nazi Germany.', @history),
('SPQR', 10.76, 17.99, 86, 'By Mary Beard. A new history of ancient Rome and its people.', @history),
('The Silk Roads', 17.64, 24.99, 61, 'By Peter Frankopan. A history of the world seen through the trade routes connecting East and West.', @history),
('A Short History of Nearly Everything', 23.56, 34.99, 98, 'By Bill Bryson. A witty tour of the history of science, from the Big Bang to the rise of civilization.', @history),
('The Guns of August', 22.20, 30.99, 47, 'By Barbara W. Tuchman. An account of the first month of World War I.', @history),
('The Warmth of Other Suns', 15.04, 20.99, 53, 'By Isabel Wilkerson. The story of the Great Migration of Black Americans out of the South.', @history),
('Genghis Khan and the Making of the Modern World', 16.33, 26.99, 93, 'By Jack Weatherford. How the Mongol leader reshaped trade, politics and culture.', @history),
('The Immortal Life of Henrietta Lacks', 16.66, 26.99, 35, 'By Rebecca Skloot. The story of the woman whose cells became a cornerstone of medical research.', @history),
('Unbroken', 18.20, 25.99, 64, 'By Laura Hillenbrand. A true story of survival: an Olympian turned airman in World War II.', @history),
('The Splendid and the Vile', 12.57, 19.99, 66, 'By Erik Larson. Churchill and the people of Britain during the Blitz.', @history),
('The Devil in the White City', 14.11, 20.99, 110, 'By Erik Larson. The Chicago World''s Fair and a serial killer, told side by side.', @history),
('Alexander Hamilton', 16.47, 23.49, 79, 'By Ron Chernow. The biography of the founding father who inspired the hit musical.', @history),
('Titan: The Life of John D. Rockefeller, Sr.', 16.87, 24.99, 0, 'By Ron Chernow. The life of the oil magnate who became one of the richest people in history.', @history),
('Churchill: Walking with Destiny', 14.30, 23.99, 74, 'By Andrew Roberts. A one-volume biography of Winston Churchill.', @history),
('Born a Crime', 10.82, 18.49, 119, 'By Trevor Noah. Stories from a childhood in apartheid-era South Africa.', @history),
('Man''s Search for Meaning', 15.70, 24.49, 138, 'By Viktor E. Frankl. A psychiatrist''s reflections on finding purpose after surviving the Nazi concentration camps.', @history),
('The Boys in the Boat', 11.99, 16.99, 56, 'By Daniel James Brown. Nine working-class American rowers pursue Olympic gold at the 1936 Berlin Games.', @history);

COMMIT;



