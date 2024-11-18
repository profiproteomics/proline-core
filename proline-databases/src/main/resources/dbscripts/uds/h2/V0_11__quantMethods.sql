-- Add Missing TMT residue tags & methods

/* ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */

INSERT INTO  quant_method(name,type,abundance_unit)  values
    ('TMT 16plex', 'isobaric_tagging', 'reporter_ion_intensity'),
    ('TMT 18plex', 'isobaric_tagging', 'reporter_ion_intensity'),
    ('TMT 16plex-Deuterated', 'isobaric_tagging', 'reporter_ion_intensity'),
    ('TMT 35plex', 'isobaric_tagging', 'reporter_ion_intensity');

-- Add Missing tag for TMT 16Plex
INSERT INTO quant_label (type, name, serialized_properties, number, quant_method_id)
 values
     ('isobaric_tag', '126', '{ "reporter_mz": 126.127726 }', 1, (SELECT id FROM quant_method WHERE name = 'TMT 16plex') ),
     ('isobaric_tag', '127N', '{ "reporter_mz": 127.124761 }', 2, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '127C', '{ "reporter_mz": 127.131081 }', 3, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '128N', '{ "reporter_mz": 128.128116 }', 4, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '128C', '{ "reporter_mz": 128.134436 }', 5, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '129N', '{ "reporter_mz": 129.131471 }', 6, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '129C', '{ "reporter_mz": 129.137790 }', 7, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '130N', '{ "reporter_mz": 130.134825 }', 8, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '130C', '{ "reporter_mz": 130.141145 }', 9, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '131N', '{ "reporter_mz": 131.138180 }', 10, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '131C', '{ "reporter_mz": 131.144500 }', 11, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '132N', '{ "reporter_mz": 132.141535 }', 12, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '132C', '{ "reporter_mz": 132.147855 }', 13, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '133N', '{ "reporter_mz": 133.144890 }', 14, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '133C', '{ "reporter_mz": 133.151210 }', 15, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')),
     ('isobaric_tag', '134N', '{ "reporter_mz": 134.148245 }', 16, (SELECT id FROM quant_method WHERE name = 'TMT 16plex'));

-- Add Missing tag for TMT 18Plex
INSERT INTO quant_label (type, name, serialized_properties, number, quant_method_id)
values
    ('isobaric_tag', '126', '{ "reporter_mz": 126.127726 }', 1, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '127N', '{ "reporter_mz": 127.124761 }', 2, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '127C', '{ "reporter_mz": 127.131081 }', 3, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '128N', '{ "reporter_mz": 128.128116 }', 4, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '128C', '{ "reporter_mz": 128.134436 }', 5, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '129N', '{ "reporter_mz": 129.131471 }', 6, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '129C', '{ "reporter_mz": 129.137790 }', 7, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '130N', '{ "reporter_mz": 130.134825 }', 8, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '130C', '{ "reporter_mz": 130.141145 }', 9, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '131N', '{ "reporter_mz": 131.138180 }', 10, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '131C', '{ "reporter_mz": 131.144500 }', 11, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '132N', '{ "reporter_mz": 132.141535 }', 12, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '132C', '{ "reporter_mz": 132.147855 }', 13, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '133N', '{ "reporter_mz": 133.144890 }', 14, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '133C', '{ "reporter_mz": 133.151210 }', 15, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '134N', '{ "reporter_mz": 134.148245 }', 16, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '134C', '{ "reporter_mz": 134.154565 }', 17, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')),
    ('isobaric_tag', '135N', '{ "reporter_mz": 135.151600 }', 18, (SELECT id FROM quant_method WHERE name = 'TMT 18plex'));

-- Add Missing tag for TMT 16plex-Deuterated
INSERT INTO quant_label (type, name, serialized_properties, number, quant_method_id)
values
    ('isobaric_tag', '127D', '{ "reporter_mz": 127.134003}',1, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '128ND', '{ "reporter_mz": 128.131038}',2, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '128CD', '{ "reporter_mz": 128.137358}',3, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '129ND', '{ "reporter_mz": 129.134393}',4, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '129CD', '{ "reporter_mz": 129.140713}',5, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '130ND', '{ "reporter_mz": 130.137748}',6, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '130CD', '{ "reporter_mz": 130.144068}',7, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '131ND', '{ "reporter_mz": 131.141103}',8, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '131CD', '{ "reporter_mz": 131.147423}',9, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),
    ('isobaric_tag', '132ND', '{ "reporter_mz": 132.144458}',10, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '132CD', '{ "reporter_mz": 132.150778}',11, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '133ND', '{ "reporter_mz": 133.147813}',12, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '133CD', '{ "reporter_mz": 133.154133}',13, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '134ND', '{ "reporter_mz": 134.151171}',14, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '134CD', '{ "reporter_mz": 134.157491}',15, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')),;
    ('isobaric_tag', '135ND', '{ "reporter_mz": 135.154526}',16, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated'));

-- Add Missing tag for TMT 35plex
INSERT INTO quant_label (type, name, serialized_properties, number, quant_method_id)
values
    ('isobaric_tag', '126', '{ "reporter_mz":126.12772}',1 ,(SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '127N', '{ "reporter_mz":127.124761 }',2, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '127C', '{ "reporter_mz":127.131081 }',3, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '127D', '{ "reporter_mz":127.134003 }',4, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '128N', '{ "reporter_mz":128.128116 }',5, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '128ND', '{ "reporter_mz":128.131038 }',6, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '128C', '{ "reporter_mz":128.134436 }',7, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '128CD', '{ "reporter_mz":128.137358 }',8, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '129N', '{ "reporter_mz":129.131471 }',9, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '129ND', '{ "reporter_mz":129.134393 }',10, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '129C', '{ "reporter_mz":129.13779 }',11, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '129CD', '{ "reporter_mz":129.140713 }',12, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '130N', '{ "reporter_mz":130.134825 }',13,(SELECT id FROM quant_method WHERE name = 'TMT 35plex') ),
    ('isobaric_tag', '130ND', '{ "reporter_mz":130.137748 }',14, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '130C', '{ "reporter_mz":130.141145 }',15, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '130CD', '{ "reporter_mz":130.144068 }',16, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '131N', '{ "reporter_mz":131.13818 }', 17, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '131ND', '{ "reporter_mz":131.141103 }',18, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '131C', '{ "reporter_mz":131.1445 }',19, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '131CD', '{ "reporter_mz":131.147423 }',20, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '132N', '{ "reporter_mz":132.141535 }',21, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '132ND', '{ "reporter_mz":132.144458 }',22, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '132C', '{ "reporter_mz":132.147855 }',23, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '132CD', '{ "reporter_mz":132.150778 }',24, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '133N', '{ "reporter_mz":133.14489 }',25, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '133ND', '{ "reporter_mz":133.147813 }',26, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '133C', '{ "reporter_mz":133.15121 }',27, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '133CD', '{ "reporter_mz":133.154133 }',28, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '134N', '{ "reporter_mz":134.148245 }',29, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '134ND', '{ "reporter_mz":134.151171 }',30, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '134C', '{ "reporter_mz":134.154565 }',31, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '134CD', '{ "reporter_mz":134.157491 }',32, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '135N', '{ "reporter_mz":135.1516 }',33, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '135ND', '{ "reporter_mz":135.154526 }',34, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')),
    ('isobaric_tag', '135CD', '{ "reporter_mz":135.160846 }',35, (SELECT id FROM quant_method WHERE name = 'TMT 35plex'));

