package com.couchsurfing.animatinglistview;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;

public class MainActivity extends Activity {

  private Button shrink;
  private Button grow;
  private AnimatingListView list;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    grow = (Button)findViewById(R.id.grow);
    shrink = (Button)findViewById(R.id.shrink);
    list = (AnimatingListView) findViewById(R.id.list);
    list.setAdapter(new FakeChatAdapter(this));
    grow.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        list.animateBy(100);
      }
    });
    shrink.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        list.animateBy(-100);
      }
    });
  }

  private static class FakeChatAdapter extends ArrayAdapter {
    static String[]  chats = {"Hi how are you?", "I am fine, but here is a long \r\n long \r\n long \r\n long \r\n long message", "wow that \r\n was \r\n long",
        "hey you \r\n can \r\n make \r\n long \r\n messages too!"};


    public FakeChatAdapter(Context context) {
      super(context, R.layout.item_list, chats);
    }

    //@Override
    //public View getView(int position, View convertView, ViewGroup parent) {
    //  Log.i("@@@", "get view: " + position);
    //  return super.getView(position, convertView, parent);
    //}
  }
}

