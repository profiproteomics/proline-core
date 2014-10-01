CREATE INDEX feature_peakel_item_peakel_idx
 ON feature_peakel_item
 ( peakel_id );

CREATE INDEX processed_map_feature_item_feature_idx
 ON processed_map_feature_item
 ( feature_id );

CREATE INDEX peakel_map_idx
 ON public.peakel
 ( map_id );

CLUSTER peakel_map_idx ON peakel;

CREATE INDEX feature_peakel_item_map_idx
 ON public.feature_peakel_item
 ( map_id );

CLUSTER feature_peakel_item_map_idx ON feature_peakel_item;