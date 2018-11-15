DROP TABLE MANUAL;
DROP TABLE LEGO_SET;

CREATE TABLE LEGO_SET ( id1 SERIAL PRIMARY KEY, NAME VARCHAR(30));
CREATE TABLE MANUAL ( id2 SERIAL PRIMARY KEY, LEGO_SET BIGINT, ALTERNATIVE BIGINT, CONTENT VARCHAR(2000));

ALTER TABLE MANUAL ADD FOREIGN KEY (LEGO_SET)
REFERENCES LEGO_SET(id1);

CREATE TABLE ONE_TO_ONE_PARENT ( id3 SERIAL PRIMARY KEY, content VARCHAR(30));
CREATE TABLE Child_No_Id (ONE_TO_ONE_PARENT INTEGER PRIMARY KEY, content VARCHAR(30));

CREATE TABLE LIST_PARENT ( id4 SERIAL PRIMARY KEY, NAME VARCHAR(100));
CREATE TABLE element_no_id ( content VARCHAR(100), LIST_PARENT_key BIGINT, LIST_PARENT INTEGER);
