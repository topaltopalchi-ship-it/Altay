-- Allow a versioned full-app backup to be stored as a single user-owned cloud record.
-- Applied to the OBA Supabase project as migration 20261024194936_oba_cloud_backup_type.

alter table public.oba_user_data drop constraint if exists oba_user_data_data_type_check;
alter table public.oba_user_data
  add constraint oba_user_data_data_type_check
  check (data_type in (
    'expense','income','purchase','inventory','budget',
    'household_task','shopping_list','reminder','vehicle_service','home_service',
    'app_backup'
  ));
