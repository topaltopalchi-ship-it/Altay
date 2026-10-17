package ir.khone.yar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends Activity {
 private static final String API="https://sxyaujvliummbvzznejq.supabase.co/functions/v1/";
 private static final String API_KEY="sb_publishable_AQdI9Ehm-lf7p1Rs7bu4BA_m60TTlen";
 private EditText username,password;
 private TextView status;
 private Button action,mode;
 private boolean signup=false,busy=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);
  SharedPreferences p=getSharedPreferences("oba_auth",MODE_PRIVATE);
  if(!p.getString("access_token","").isEmpty()&&p.getLong("expires_at",0)>System.currentTimeMillis()){openApp();return;}
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(36,48,36,24);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setBackgroundColor(Color.rgb(244,248,255));
  TextView title=new TextView(this);title.setText("اوبا • OBA");title.setTextSize(30);title.setTextColor(Color.rgb(32,52,105));title.setGravity(Gravity.CENTER);box.addView(title);
  username=new EditText(this);username.setSingleLine(true);username.setHint("نام کاربری انگلیسی (۳ تا ۲۴ حرف)");box.addView(username,new LinearLayout.LayoutParams(-1,-2));
  password=new EditText(this);password.setSingleLine(true);password.setHint("رمز عبور (حداقل ۸ کاراکتر)");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(password,new LinearLayout.LayoutParams(-1,-2));
  action=new Button(this);action.setText("ورود");action.setOnClickListener(v->submit());box.addView(action,new LinearLayout.LayoutParams(-1,-2));
  mode=new Button(this);mode.setText("حساب نداری؟ ثبت‌نام کن");mode.setOnClickListener(v->{signup=!signup;action.setText(signup?"ساخت حساب":"ورود");mode.setText(signup?"حساب داری؟ وارد شو":"حساب نداری؟ ثبت‌نام کن");});box.addView(mode,new LinearLayout.LayoutParams(-1,-2));
  status=new TextView(this);status.setText("اطلاعات مالی فعلی همچنان روی همین گوشی ذخیره می‌شوند.");status.setGravity(Gravity.CENTER);box.addView(status,new LinearLayout.LayoutParams(-1,-2));
  setContentView(box);
 }
 private void submit(){if(busy)return;String u=username.getText().toString().trim().toLowerCase(java.util.Locale.ROOT),p=password.getText().toString();
  if(!u.matches("[a-z0-9_]{3,24}")){username.setError("۳ تا ۲۴ حرف انگلیسی، عدد یا _");return;}if(p.length()<8||p.length()>72){password.setError("رمز عبور باید ۸ تا ۷۲ کاراکتر باشد");return;}
  busy=true;action.setEnabled(false);final boolean create=signup;
  new Thread(()->{try{JSONObject result=request(create?"register-username":"login-username",u,p);
   if(create){runOnUiThread(()->{busy=false;action.setEnabled(true);signup=false;action.setText("ورود");mode.setText("حساب نداری؟ ثبت‌نام کن");password.setText("");status.setText("حساب ساخته شد؛ اکنون وارد شو.");});}
   else{long seconds=result.optLong("expires_in",3600);getSharedPreferences("oba_auth",MODE_PRIVATE).edit().putString("access_token",result.getString("access_token")).putString("refresh_token",result.optString("refresh_token","")).putString("username",u).putLong("expires_at",System.currentTimeMillis()+seconds*1000L).apply();runOnUiThread(this::openApp);}
  }catch(Exception e){String m=e.getMessage()==null?"ارتباط با سرور برقرار نشد.":e.getMessage();runOnUiThread(()->{busy=false;action.setEnabled(true);status.setText(m);});}}).start();
 }
 private JSONObject request(String fn,String u,String p)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+fn).openConnection();try{c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("apikey",API_KEY);JSONObject b=new JSONObject();b.put("username",u);b.put("password",p);try(OutputStream o=c.getOutputStream()){o.write(b.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream s=code>=200&&code<400?c.getInputStream():c.getErrorStream();StringBuilder raw=new StringBuilder();if(s!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)raw.append(line);}JSONObject out=raw.length()==0?new JSONObject():new JSONObject(raw.toString());if(code<200||code>=300)throw new Exception(out.optString("error","خطای سرور "+code));return out;}finally{c.disconnect();}}
 private void openApp(){Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(i);finish();}
}