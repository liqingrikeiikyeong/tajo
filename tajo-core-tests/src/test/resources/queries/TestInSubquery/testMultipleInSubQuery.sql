select n_name from nation
where
  n_regionkey in (select r_regionkey from region)
  and
  n_nationkey in (select s_nationkey from supplier)