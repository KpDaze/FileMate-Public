package app.filemate.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Local test exporter. No network, AI provider account or private user data. */
public class MainActivity extends Activity {
    TextView status;
    String provider;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        provider=getPackageName().endsWith("qwen") ? "Qwen" : "ChatGPT";
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,36,24,24);
        TextView title=new TextView(this);title.setText(provider+" TEST EXPORTER");title.setTextSize(24);root.addView(title);
        TextView description=new TextView(this);description.setText("Synthetic Android downloads for FileMate verification. This is not an AI provider app.");root.addView(description);
        button(root,"Save AI file",()->save(provider+"_fixture_","txt","text/plain"));
        button(root,"Save unrelated receipt",()->save("receipt_","pdf","application/pdf"));
        button(root,"Save ambiguous notes",()->save("notes_","txt","text/plain"));
        button(root,"Switch test app",()->{
            String other=getPackageName().endsWith("qwen")?"app.filemate.test.chatgpt":"app.filemate.test.qwen";
            android.content.Intent i=getPackageManager().getLaunchIntentForPackage(other);
            if(i!=null)startActivity(i);else status.setText("Other fixture is not installed.");
        });
        status=new TextView(this);status.setText("Ready to export into Downloads/FileMateTests.");root.addView(status);
        setContentView(root);
    }
    void button(LinearLayout root,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setOnClickListener(v->action.run());root.addView(button);}
    void save(String prefix,String extension,String mime){
        String name=prefix+System.currentTimeMillis()+"."+extension;
        Uri uri=null;
        try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/FileMateTests");v.put(MediaStore.Downloads.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null)throw new IllegalStateException("MediaStore insert failed");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(("Synthetic FileMate verification content.\n"+name+"\n").getBytes(StandardCharsets.UTF_8));}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,done,null,null);
            status.setText("Saved: "+name);
        }catch(Exception e){status.setText("Export failed: "+e.toString());}
    }
}
