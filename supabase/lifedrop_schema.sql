-- LifeDrop Supabase schema
-- Run this entire script in Supabase Dashboard -> SQL Editor -> New query.

create table if not exists public.donors (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  blood_group text not null check (blood_group in ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
  phone text not null,
  city text not null,
  available boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.blood_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  patient text not null,
  blood_group text not null check (blood_group in ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
  units integer not null check (units > 0),
  hospital text not null,
  city text not null,
  phone text not null,
  urgency text not null default 'Urgent',
  status text not null default 'Open' check (status in ('Open','Fulfilled')),
  created_at timestamptz not null default now()
);

alter table public.donors enable row level security;
alter table public.blood_requests enable row level security;

drop policy if exists "Anyone can view available donors" on public.donors;
create policy "Anyone can view available donors" on public.donors
for select using (available = true or auth.uid() = owner_id);

drop policy if exists "Users can add their own donor profile" on public.donors;
create policy "Users can add their own donor profile" on public.donors
for insert to authenticated with check (auth.uid() = owner_id);

drop policy if exists "Users can update their own donor profile" on public.donors;
create policy "Users can update their own donor profile" on public.donors
for update to authenticated using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

drop policy if exists "Users can delete their own donor profile" on public.donors;
create policy "Users can delete their own donor profile" on public.donors
for delete to authenticated using (auth.uid() = owner_id);

drop policy if exists "Anyone can view blood requests" on public.blood_requests;
create policy "Anyone can view blood requests" on public.blood_requests
for select using (true);

drop policy if exists "Authenticated users can post requests" on public.blood_requests;
create policy "Authenticated users can post requests" on public.blood_requests
for insert to authenticated with check (auth.uid() = owner_id);

drop policy if exists "Users can update their own requests" on public.blood_requests;
create policy "Users can update their own requests" on public.blood_requests
for update to authenticated using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

drop policy if exists "Users can delete their own requests" on public.blood_requests;
create policy "Users can delete their own requests" on public.blood_requests
for delete to authenticated using (auth.uid() = owner_id);

grant usage on schema public to anon, authenticated;
grant select on public.donors, public.blood_requests to anon, authenticated;
grant insert, update, delete on public.donors, public.blood_requests to authenticated;

-- Public usernames: unique without regard to letter case.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null,
  created_at timestamptz not null default now()
);
create unique index if not exists profiles_username_lower_unique on public.profiles (lower(username));

-- Create a profile for existing accounts as well as new ones.
insert into public.profiles (id, username)
select u.id,
       coalesce(nullif(u.raw_user_meta_data->>'username',''),
                'user_' || replace(left(u.id::text, 8),'-',''))
from auth.users u
on conflict (id) do nothing;

alter table public.profiles enable row level security;
drop policy if exists "Anyone can view usernames" on public.profiles;
create policy "Anyone can view usernames" on public.profiles for select using (true);
drop policy if exists "Users can create their own profile" on public.profiles;
create policy "Users can create their own profile" on public.profiles
for insert to authenticated with check (auth.uid() = id);
drop policy if exists "Users can update their own profile" on public.profiles;
create policy "Users can update their own profile" on public.profiles
for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- Link requests to profiles so the request author's username can be displayed.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'blood_requests_owner_profile_fkey') then
    alter table public.blood_requests
      add constraint blood_requests_owner_profile_fkey
      foreign key (owner_id) references public.profiles(id) on delete cascade;
  end if;
end $$;

grant select on public.profiles to anon, authenticated;
grant insert, update on public.profiles to authenticated;
