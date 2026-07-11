-- Eén enkele rij: er is maar 1 set notities, gewoon een lange string (zie NotesService).
CREATE TABLE notes (
    id BIGINT PRIMARY KEY,
    text TEXT NOT NULL DEFAULT ''
);

INSERT INTO notes (id, text) VALUES (1, '');
