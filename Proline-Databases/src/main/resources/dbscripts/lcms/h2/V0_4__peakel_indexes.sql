CREATE INDEX feature_peakel_item_peakel_idx
 ON feature_peakel_item
 ( peakel_id );

CREATE INDEX processed_map_feature_item_feature_idx
 ON processed_map_feature_item
 ( feature_id );

CREATE INDEX peakel_map_idx
 ON peakel
 ( map_id );

CREATE INDEX feature_peakel_item_map_idx
 ON feature_peakel_item
 ( map_id );