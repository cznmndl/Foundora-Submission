-- Photos are now served through the app, which checks who may see each one.
-- The server re-encodes every upload as JPEG, so that is the only type the bucket accepts.
update storage.buckets
   set public             = false,
       file_size_limit    = 5242880,
       allowed_mime_types = array['image/jpeg']
 where id = 'item-images';
