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

  if (!/^[a-z0-9_]{3,24}$/.test(username)) {
    return response({ error: "نام کاربری باید ۳ تا ۲۴ حرف انگلیسی کوچک، عدد یا زیرخط باشد." }, 400);
  }
  if (password.length < 8 || password.length > 72) {
    return response({ error: "رمز عبور باید حداقل ۸ کاراکتر داشته باشد." }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return response({ error: "تنظیمات سرور کامل نیست." }, 500);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });

  const syntheticEmail = `${username}@users.oba.invalid`;
  const { data, error } = await admin.auth.admin.createUser({
    email: syntheticEmail,
    password,
    email_confirm: true,
    user_metadata: { username },
  });

  if (error || !data.user) {
    // Keep responses generic so account enumeration is harder.
    return response({ error: "ثبت‌نام انجام نشد. نام کاربری ممکن است قبلاً استفاده شده باشد." }, 409);
  }

  return response({ ok: true, message: "حساب ساخته شد. اکنون وارد شوید." }, 201);
});
