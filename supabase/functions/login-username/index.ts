import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return response({ error: "Method not allowed" }, 405);

  let payload: { username?: unknown; password?: unknown };
  try {
    payload = await req.json();
  } catch {
    return response({ error: "درخواست نامعتبر است." }, 400);
  }

  const username = typeof payload.username === "string"
    ? payload.username.trim().toLowerCase()
    : "";
  const password = typeof payload.password === "string" ? payload.password : "";
  if (!/^[a-z0-9_]{3,24}$/.test(username) || password.length < 8 || password.length > 72) {
    return response({ error: "نام کاربری یا رمز عبور نادرست است." }, 400);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url || !anonKey) return response({ error: "تنظیمات ورود سرور کامل نیست." }, 500);

  // The synthetic address is internal only; users still log in with their username.
  const client = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data, error } = await client.auth.signInWithPassword({
    email: `${username}@users.oba.invalid`,
    password,
  });

  if (error || !data.session) {
    return response({ error: "نام کاربری یا رمز عبور نادرست است." }, 401);
  }

  return response({
    access_token: data.session.access_token,
    refresh_token: data.session.refresh_token,
    token_type: data.session.token_type,
    expires_in: data.session.expires_in,
    user: { id: data.user.id, username },
  });
});
