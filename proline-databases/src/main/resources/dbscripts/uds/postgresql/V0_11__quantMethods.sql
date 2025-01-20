select setval('quant_method_id_seq', (select max(id)+1 as nextSeq from quant_method qm));
select setval('quant_label_id_seq', (select max(id)+1 as nextSeq from quant_label ql ));

-- Add Missing TMT methods > TMT11 Plex if needed
WITH tmt_methods(name,type,abundance_unit) as (
    values
        ('TMT 16plex', 'isobaric_tagging', 'reporter_ion_intensity'),
        ('TMT 18plex', 'isobaric_tagging', 'reporter_ion_intensity'),
        ('TMT 16plex-Deuterated', 'isobaric_tagging', 'reporter_ion_intensity'),
        ('TMT 35plex', 'isobaric_tagging', 'reporter_ion_intensity')
)

INSERT INTO quant_method (name,type, abundance_unit)
SELECT name,type,abundance_unit
FROM tmt_methods
WHERE NOT EXISTS (SELECT 1
                  FROM quant_method qm
                  WHERE qm.name = tmt_methods.name);


-- Add Missing tag for TMT 16Plex
WITH tags( type, name, serialized_properties, number) as (
    values
        ('isobaric_tag',    '126', '{ "reporter_mz": 126.127726   }',    1 ),
        ('isobaric_tag',    '127N' ,'{ "reporter_mz": 127.124761   }',    2 ),
        ('isobaric_tag',    '127C' ,'{ "reporter_mz": 127.131081   }',    3 ),
        ('isobaric_tag',    '128N' ,'{ "reporter_mz": 128.128116   }',    4 ),
        ('isobaric_tag',    '128C' ,'{ "reporter_mz": 128.134436   }',    5 ),
        ('isobaric_tag',    '129N' ,'{ "reporter_mz": 129.131471   }',    6 ),
        ('isobaric_tag',    '129C' ,'{ "reporter_mz": 129.137790   }',    7 ),
        ('isobaric_tag',    '130N' ,'{ "reporter_mz": 130.134825   }',    8 ),
        ('isobaric_tag',    '130C' ,'{ "reporter_mz": 130.141145   }',    9 ),
        ('isobaric_tag',    '131N' ,'{ "reporter_mz": 131.138180   }',    10),
        ('isobaric_tag',    '131C' ,'{ "reporter_mz": 131.144500   }',    11),
        ('isobaric_tag',    '132N' ,'{ "reporter_mz": 132.141535   }',    12),
        ('isobaric_tag',    '132C' ,'{ "reporter_mz": 132.147855   }',    13),
        ('isobaric_tag',    '133N' ,'{ "reporter_mz": 133.144890   }',    14),
        ('isobaric_tag',    '133C' ,'{ "reporter_mz": 133.151210   }',    15),
        ('isobaric_tag',    '134N' ,'{ "reporter_mz": 134.148245   }',    16)
)
INSERT INTO quant_label (type, name, number, serialized_properties, quant_method_id)
SELECT type, name, number, serialized_properties, (SELECT id FROM quant_method WHERE name = 'TMT 16plex')
FROM tags
WHERE NOT EXISTS (SELECT 1
                  FROM quant_label ql
                  WHERE ql.quant_method_id = (SELECT id FROM quant_method WHERE name = 'TMT 16plex'));

-- Add Missing tag for TMT 18Plex
WITH tags( type, name, serialized_properties, number) as (
    values
        ('isobaric_tag',    '126',    '{ "reporter_mz": 126.127726   }',    1 ),
        ('isobaric_tag',    '127N',    '{ "reporter_mz": 127.124761   }',    2 ),
        ('isobaric_tag',    '127C',    '{ "reporter_mz": 127.131081   }',    3 ),
        ('isobaric_tag',    '128N',    '{ "reporter_mz": 128.128116   }',    4 ),
        ('isobaric_tag',    '128C',    '{ "reporter_mz": 128.134436   }',    5 ),
        ('isobaric_tag',    '129N',    '{ "reporter_mz": 129.131471   }',    6 ),
        ('isobaric_tag',    '129C',    '{ "reporter_mz": 129.137790   }',    7 ),
        ('isobaric_tag',    '130N',    '{ "reporter_mz": 130.134825   }',    8 ),
        ('isobaric_tag',    '130C',    '{ "reporter_mz": 130.141145   }',    9 ),
        ('isobaric_tag',    '131N',    '{ "reporter_mz": 131.138180   }',    10),
        ('isobaric_tag',    '131C',    '{ "reporter_mz": 131.144500   }',    11),
        ('isobaric_tag',    '132N',    '{ "reporter_mz": 132.141535   }',    12),
        ('isobaric_tag',    '132C',    '{ "reporter_mz": 132.147855   }',    13),
        ('isobaric_tag',    '133N',    '{ "reporter_mz": 133.144890   }',    14),
        ('isobaric_tag',    '133C',    '{ "reporter_mz": 133.151210   }',    15),
        ('isobaric_tag',    '134N',    '{ "reporter_mz": 134.148245   }',    16),
        ('isobaric_tag',    '134C',    '{ "reporter_mz": 134.154565   }',    17),
        ('isobaric_tag',    '135N',    '{ "reporter_mz": 135.151600   }',    18)
)
INSERT INTO quant_label (type, name, number, serialized_properties, quant_method_id)
SELECT type, name, number, serialized_properties, (SELECT id FROM quant_method WHERE name = 'TMT 18plex')
FROM tags
WHERE NOT EXISTS (SELECT 1
                  FROM quant_label ql
                  WHERE ql.quant_method_id = (SELECT id FROM quant_method WHERE name = 'TMT 18plex'));

-- Add Missing tag for TMT 16plex-Deuterated
WITH tags( type, name, serialized_properties, number) as (
    values
        ('isobaric_tag', '127D',    '{ "reporter_mz": 127.134003}',1 ),
        ('isobaric_tag', '128ND',    '{ "reporter_mz": 128.131038}',2 ),
        ('isobaric_tag', '128CD',    '{ "reporter_mz": 128.137358}',3 ),
        ('isobaric_tag', '129ND',    '{ "reporter_mz": 129.134393}',4 ),
        ('isobaric_tag', '129CD',    '{ "reporter_mz": 129.140713}',5 ),
        ('isobaric_tag', '130ND',    '{ "reporter_mz": 130.137748}',6 ),
        ('isobaric_tag', '130CD',    '{ "reporter_mz": 130.144068}',7 ),
        ('isobaric_tag', '131ND',    '{ "reporter_mz": 131.141103}',8 ),
        ('isobaric_tag', '131CD',    '{ "reporter_mz": 131.147423}',9 ),
        ('isobaric_tag', '132ND',    '{ "reporter_mz": 132.144458}',10),
        ('isobaric_tag', '132CD',    '{ "reporter_mz": 132.150778}',11),
        ('isobaric_tag', '133ND',    '{ "reporter_mz": 133.147813}',12),
        ('isobaric_tag', '133CD',    '{ "reporter_mz": 133.154133}',13),
        ('isobaric_tag', '134ND',    '{ "reporter_mz": 134.151171}',14),
        ('isobaric_tag', '134CD',    '{ "reporter_mz": 134.157491}',15),
        ('isobaric_tag', '135ND',    '{ "reporter_mz": 135.154526}',16)
)
INSERT INTO quant_label (type, name, number, serialized_properties, quant_method_id)
SELECT type, name, number, serialized_properties, (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated')
FROM tags
WHERE NOT EXISTS (SELECT 1
                  FROM quant_label ql
                  WHERE ql.quant_method_id = (SELECT id FROM quant_method WHERE name = 'TMT 16plex-Deuterated'));



-- Add Missing tag for TMT 35plex
WITH tags( type, name, serialized_properties, number) as (
    values
        ('isobaric_tag', '126', '{ "reporter_mz":126.12772}',1 ),
        ('isobaric_tag', '127N', '{ "reporter_mz":127.124761 }',2 ),
        ('isobaric_tag', '127C', '{ "reporter_mz":127.131081 }',3 ),
        ('isobaric_tag', '127D', '{ "reporter_mz":127.134003 }',4 ),
        ('isobaric_tag', '128N', '{ "reporter_mz":128.128116 }',5 ),
        ('isobaric_tag', '128ND', '{ "reporter_mz":128.131038 }',6 ),
        ('isobaric_tag', '128C', '{ "reporter_mz":128.134436 }',7 ),
        ('isobaric_tag', '128CD', '{ "reporter_mz":128.137358 }',8 ),
        ('isobaric_tag', '129N', '{ "reporter_mz":129.131471 }',9 ),
        ('isobaric_tag', '129ND', '{ "reporter_mz":129.134393 }',10),
        ('isobaric_tag', '129C', '{ "reporter_mz":129.13779 }',11),
        ('isobaric_tag', '129CD', '{ "reporter_mz":129.140713 }',12),
        ('isobaric_tag', '130N', '{ "reporter_mz":130.134825 }',13),
        ('isobaric_tag', '130ND', '{ "reporter_mz":130.137748 }',14),
        ('isobaric_tag', '130C', '{ "reporter_mz":130.141145 }',15),
        ('isobaric_tag', '130CD', '{ "reporter_mz":130.144068 }',16),
        ('isobaric_tag', '131N', '{ "reporter_mz":131.13818 }', 17),
        ('isobaric_tag', '131ND', '{ "reporter_mz":131.141103 }',18),
        ('isobaric_tag', '131C', '{ "reporter_mz":131.1445 }',19),
        ('isobaric_tag', '131CD', '{ "reporter_mz":131.147423 }',20 ),
        ('isobaric_tag', '132N', '{ "reporter_mz":132.141535 }',21 ),
        ('isobaric_tag', '132ND', '{ "reporter_mz":132.144458 }',22 ),
        ('isobaric_tag', '132C', '{ "reporter_mz":132.147855 }',23),
        ('isobaric_tag', '132CD', '{ "reporter_mz":132.150778 }',24),
        ('isobaric_tag', '133N', '{ "reporter_mz":133.14489 }',25),
        ('isobaric_tag', '133ND', '{ "reporter_mz":133.147813 }',26),
        ('isobaric_tag', '133C', '{ "reporter_mz":133.15121 }',27),
        ('isobaric_tag', '133CD', '{ "reporter_mz":133.154133 }',28),
        ('isobaric_tag', '134N', '{ "reporter_mz":134.148245 }',29),
        ('isobaric_tag', '134ND', '{ "reporter_mz":134.151171 }',30),
        ('isobaric_tag', '134C', '{ "reporter_mz":134.154565 }',31),
        ('isobaric_tag', '134CD', '{ "reporter_mz":134.157491 }',32),
        ('isobaric_tag', '135N', '{ "reporter_mz":135.1516 }',33),
        ('isobaric_tag', '135ND', '{ "reporter_mz":135.154526 }',34),
        ('isobaric_tag', '135CD', '{ "reporter_mz":135.160846 }',35)
)
INSERT INTO quant_label (type, name, number, serialized_properties, quant_method_id)
SELECT type, name, number, serialized_properties, (SELECT id FROM quant_method WHERE name = 'TMT 35plex')
FROM tags
WHERE NOT EXISTS (SELECT 1
                  FROM quant_label ql
                  WHERE ql.quant_method_id = (SELECT id FROM quant_method WHERE name = 'TMT 35plex'));