CREATE INDEX peakel_map_idx
 ON public.peakel
 ( map_id );

CLUSTER peakel_map_idx ON peakel;

CREATE INDEX feature_peakel_item_map_idx
 ON public.feature_peakel_item
 ( map_id );

CLUSTER feature_peakel_item_map_idx ON feature_peakel_item;