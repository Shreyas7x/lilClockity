package com.example.blacktableclock;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    int bg=0xFF101820, fg=Color.WHITE, accent=0xFF8DE4CB;
    int originalBg=bg, originalFg=fg, originalAccent=accent;
    int page=0; // 0 home, 1 stopwatch, 2 timer, 3 calendar
    boolean night=false, swRunning=false, timerRunning=false;
    float brightnessBeforeNight = -1f;
    long swStart=0, swElapsed=0, timerEnd=0, timerDuration=300000;
    int[] presets={60000,300000,600000};
    SharedPreferences prefs;
    ToneGenerator toneGenerator;
    Handler handler=new Handler();
    LinearLayout root, homePanel, sideClock, toolPanel;
    TextView homeTime, homeDay, homeDate, sideTime, sideDay, sideDate, toolTime, timerStartButton, swStartButton;
    float downX,downY; boolean swipeHandled=false;
    GestureDetector homeGesture;
    Runnable tick=new Runnable(){public void run(){updateClock();updateTool();handler.postDelayed(this,250);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);WindowManager.LayoutParams launchLp=getWindow().getAttributes();launchLp.screenBrightness=1.0f;getWindow().setAttributes(launchLp);
        prefs=getSharedPreferences("clock_settings",MODE_PRIVATE);
        for(int i=0;i<3;i++)presets[i]=prefs.getInt("preset_"+i,presets[i]);
        bg=prefs.getInt("theme_bg",bg);fg=prefs.getInt("theme_fg",fg);accent=prefs.getInt("theme_accent",accent);
        originalBg=bg;originalFg=fg;originalAccent=accent;
        try{toneGenerator=new ToneGenerator(AudioManager.STREAM_ALARM,90);}catch(Exception ignored){}
        build();handler.post(tick);}
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(fg);t.setTextSize(size);t.setGravity(Gravity.CENTER);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setGravity(Gravity.CENTER);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER);return l;}
    void build(){
        root=row();root.setBackgroundColor(bg);setContentView(root);
        homePanel=column();homePanel.setPadding(dp(2),dp(2),dp(2),dp(2));
        homeDay=text("Saturday",38,true);homeTime=text("12:00",244,true);homeDate=text("24 October",26,false);
        homePanel.addView(homeDay,new LinearLayout.LayoutParams(-1,0,0.85f));
        homePanel.addView(homeTime,new LinearLayout.LayoutParams(-1,0,5.3f));
        homePanel.addView(homeDate,new LinearLayout.LayoutParams(-1,0,0.85f));
        homeDate.setOnClickListener(v->showPage(3));
        homeGesture=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public void onLongPress(android.view.MotionEvent e){showOptions();}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){toggleManualNightMode();return true;}
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
        });
        homePanel.setOnTouchListener((v,e)->{homeGesture.onTouchEvent(e);return true;});
        showPage(0);updateClock();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent ev){
        if(page==0){
            if(ev.getAction()==MotionEvent.ACTION_DOWN){downX=ev.getX();downY=ev.getY();swipeHandled=false;}
            else if(ev.getAction()==MotionEvent.ACTION_UP&&!swipeHandled){
                float dx=ev.getX()-downX,dy=ev.getY()-downY;
                if(Math.abs(dx)>dp(65)&&Math.abs(dx)>Math.abs(dy)*1.25f){
                    swipeHandled=true;showPage(dx<0?1:2);return true;
                }
            }
        } else if(page==1 || page==2) {
            if(ev.getAction()==MotionEvent.ACTION_DOWN){downX=ev.getX();downY=ev.getY();swipeHandled=false;}
            else if(ev.getAction()==MotionEvent.ACTION_UP&&!swipeHandled){
                float dx=ev.getX()-downX,dy=ev.getY()-downY;
                boolean returnSwipe = (page==1 && dx>dp(65)) || (page==2 && dx< -dp(65));
                if(returnSwipe && Math.abs(dx)>Math.abs(dy)*1.25f){swipeHandled=true;showPage(0);return true;}
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    void showPage(int p){
        page=p;root.removeAllViews();root.setBackgroundColor(bg);
        if(p==0){
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(homePanel,new LinearLayout.LayoutParams(-1,-1));
            homePanel.setBackgroundColor(bg);
        } else if(p==3){
            root.setOrientation(LinearLayout.VERTICAL);
            buildCalendarFull();
        } else {
            root.setOrientation(LinearLayout.HORIZONTAL);
            sideClock=column();sideClock.setPadding(dp(2),dp(2),dp(2),dp(2));
            sideDay=text("Saturday",16,true);sideTime=text("12:00",52,true);sideDate=text("24 Oct",14,false);
            sideClock.addView(sideDay,new LinearLayout.LayoutParams(-1,0,1));
            sideClock.addView(sideTime,new LinearLayout.LayoutParams(-1,0,3));
            sideClock.addView(sideDate,new LinearLayout.LayoutParams(-1,0,1));
            sideClock.setBackgroundColor(bg);
            toolPanel=column();toolPanel.setBackgroundColor(bg);
            LinearLayout.LayoutParams clockLp=new LinearLayout.LayoutParams(0,-1,0.30f);
            LinearLayout.LayoutParams toolLp=new LinearLayout.LayoutParams(0,-1,0.70f);
            View separator=new View(this);
            separator.setBackgroundColor(night?0xFF666666:blendSeparator(bg,fg));
            LinearLayout.LayoutParams separatorLp=new LinearLayout.LayoutParams(dp(2),-1);
            if(p==1){root.addView(sideClock,clockLp);root.addView(separator,separatorLp);root.addView(toolPanel,toolLp);buildStopwatch();}
            else {root.addView(toolPanel,toolLp);root.addView(separator,separatorLp);root.addView(sideClock,clockLp);buildTimer();}
            sideDate.setOnClickListener(v->showPage(3));
            sideClock.setOnClickListener(v->showPage(0));
            root.setAlpha(.9f);root.animate().alpha(1f).setDuration(160).start();
        }
        updateClock();
    }
    TextView actionButton(String label){TextView b=text(label,16,true);b.setTextColor(Color.BLACK);
        b.setBackground(shape(accent,22));
        b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);return b;}
    void buildStopwatch(){
        toolPanel.removeAllViews();
        toolPanel.setPadding(0,dp(14),0,0);
        toolPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        toolPanel.addView(text("STOPWATCH",24,true),new LinearLayout.LayoutParams(-1,dp(42)));
        toolTime=text("00:00.00",76,true);
        LinearLayout.LayoutParams timeLp=new LinearLayout.LayoutParams(-1,0,1f);
        timeLp.setMargins(dp(4),dp(4),dp(4),dp(4));
        toolPanel.addView(toolTime,timeLp);

        LinearLayout controls=column();
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,-2);
        controlsLp.setMargins(dp(6),dp(8),dp(6),dp(18));

        swStartButton=actionButton("START");
        GradientDrawable circle=new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accent);
        swStartButton.setBackground(circle);
        swStartButton.setTextSize(17);
        LinearLayout.LayoutParams startLp=new LinearLayout.LayoutParams(dp(136),dp(136));
        startLp.gravity=Gravity.CENTER_HORIZONTAL;
        controls.addView(swStartButton,startLp);
        swStartButton.setOnClickListener(v->{if(swRunning){swElapsed+=System.currentTimeMillis()-swStart;swRunning=false;swStartButton.setText("START");}else{swStart=System.currentTimeMillis();swRunning=true;swStartButton.setText("STOP");}});

        TextView reset=actionButton("RESET");
        reset.setTextSize(15);
        LinearLayout.LayoutParams resetLp=new LinearLayout.LayoutParams(dp(112),dp(64));
        resetLp.gravity=Gravity.CENTER_HORIZONTAL;
        resetLp.topMargin=dp(20);
        controls.addView(reset,resetLp);
        reset.setOnClickListener(v->{swRunning=false;swElapsed=0;swStart=0;swStartButton.setText("START");updateTool();});
        toolPanel.addView(controls,controlsLp);
    }
    void buildTimer(){
        toolPanel.removeAllViews();
        toolPanel.setPadding(0,dp(14),0,0);
        toolPanel.addView(text("TIMER",24,true),new LinearLayout.LayoutParams(-1,dp(42)));
        toolTime=text(format(timerDuration),64,true);
        toolTime.setBackground(shape(bg,12));
        toolTime.setOnClickListener(v->editTimerDuration());
        toolTime.setContentDescription("Tap to set timer duration");
        LinearLayout.LayoutParams timeLp=new LinearLayout.LayoutParams(-1,0,1f);
        timeLp.setMargins(dp(4),dp(2),dp(4),dp(2));
        toolPanel.addView(toolTime,timeLp);

        timerStartButton=actionButton(timerRunning?"STOP":"START");
        GradientDrawable timerCircle=new GradientDrawable();
        timerCircle.setShape(GradientDrawable.OVAL);
        timerCircle.setColor(accent);
        timerStartButton.setBackground(timerCircle);
        timerStartButton.setTextSize(18);
        LinearLayout.LayoutParams startLp=new LinearLayout.LayoutParams(dp(132),dp(132));
        startLp.gravity=Gravity.CENTER_HORIZONTAL;
        startLp.setMargins(0,dp(8),0,dp(12));
        toolPanel.addView(timerStartButton,startLp);
        timerStartButton.setOnClickListener(v->{if(timerRunning){timerRunning=false;timerStartButton.setText("START");}else{if(timerDuration<=0){editTimerDuration();return;}timerEnd=System.currentTimeMillis()+timerDuration;timerRunning=true;timerStartButton.setText("STOP");}});

        LinearLayout buttons=row();
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams presetsLp=new LinearLayout.LayoutParams(-1,dp(88));
        presetsLp.setMargins(dp(2),dp(2),dp(2),dp(10));
        for(int i=0;i<3;i++){
            final int idx=i;
            TextView b=actionButton(format(presets[i]));
            b.setTextSize(12);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(76),dp(76));
            lp.setMargins(dp(4),dp(2),dp(4),dp(2));
            buttons.addView(b,lp);
            b.setOnClickListener(v->{timerDuration=presets[idx];timerRunning=false;timerStartButton.setText("START");toolTime.setText(format(timerDuration));});
            b.setOnLongClickListener(v->{editPreset(idx);return true;});
        }
        toolPanel.addView(buttons,presetsLp);
    }
    void buildCalendarFull(){
        Calendar c=Calendar.getInstance();

        // Size the square date cells to use the available screen while leaving room
        // for the month heading and the return button.
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenW=(int)(dm.widthPixels/dm.density);
        int screenH=(int)(dm.heightPixels/dm.density);
        int cell=Math.max(30,Math.min((screenW-24)/7,(screenH-168)/9));
        int cellPx=dp(cell);

        toolPanel=column();
        toolPanel.setGravity(Gravity.CENTER);
        toolPanel.setPadding(dp(8),dp(4),dp(8),dp(18));
        root.addView(toolPanel,new LinearLayout.LayoutParams(-1,-1));

        LinearLayout calendar=column();
        calendar.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams calendarLp=new LinearLayout.LayoutParams(cellPx*7,-2);
        calendarLp.gravity=Gravity.CENTER;
        toolPanel.addView(calendar,calendarLp);

        TextView monthTitle=text(new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(c.getTime()),
                Math.max(20,Math.min(30,cell-10)),true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,dp(Math.max(42,cell)));
        titleLp.bottomMargin=dp(Math.max(4,cell/7));
        calendar.addView(monthTitle,titleLp);

        LinearLayout headings=row();
        headings.setGravity(Gravity.CENTER);
        String[] days={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        for(String d:days){
            TextView heading=text(d,Math.max(13,Math.min(18,cell/3)),true);
            if("Sun".equals(d))heading.setTextColor(Color.RED);
            headings.addView(heading,new LinearLayout.LayoutParams(cellPx,cellPx));
        }
        calendar.addView(headings);

        Calendar first=(Calendar)c.clone();
        first.set(Calendar.DAY_OF_MONTH,1);
        int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;
        int count=c.getActualMaximum(Calendar.DAY_OF_MONTH);
        for(int w=0;w<6;w++){
            LinearLayout line=row();
            line.setGravity(Gravity.CENTER);
            for(int d=0;d<7;d++){
                int n=w*7+d-offset+1;
                TextView cellView=text(n>0&&n<=count?""+n:"",
                        Math.max(16,Math.min(24,cell/3)),n==c.get(Calendar.DAY_OF_MONTH));
                if(n==c.get(Calendar.DAY_OF_MONTH))cellView.setBackground(shape(accent,dp(8)));
                line.addView(cellView,new LinearLayout.LayoutParams(cellPx,cellPx));
            }
            calendar.addView(line);
        }

        TextView back=text("‹  CLOCK",18,true);
        back.setTextColor(fg);
        back.setBackground(shape(accent,18));
        back.setPadding(dp(24),dp(6),dp(24),dp(6));
        LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(-2,dp(Math.max(40,cell-8)));
        backLp.topMargin=dp(Math.max(5,cell/7));
        backLp.bottomMargin=dp(10);
        backLp.gravity=Gravity.CENTER_HORIZONTAL;
        calendar.addView(back,backLp);
        back.setOnClickListener(v->showPage(0));
    }
    void updateClock(){
        Calendar c=Calendar.getInstance();String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(c.getTime());
        if(homeTime!=null){homeTime.setText(time);homeDay.setText(new SimpleDateFormat("EEEE",Locale.getDefault()).format(c.getTime()));homeDate.setText(new SimpleDateFormat("d MMMM",Locale.getDefault()).format(c.getTime()));}
        if(sideTime!=null){sideTime.setText(time);sideDay.setText(new SimpleDateFormat("EEE",Locale.getDefault()).format(c.getTime()));sideDate.setText(new SimpleDateFormat("d MMM",Locale.getDefault()).format(c.getTime()));}
        for(TextView t:new TextView[]{homeTime,homeDay,homeDate,sideTime,sideDay,sideDate,toolTime})if(t!=null)t.setTextColor(fg);
    }
    void updateTool(){
        if(toolTime==null)return;
        if(page==1){long ms=swElapsed+(swRunning?System.currentTimeMillis()-swStart:0);toolTime.setText(String.format(Locale.getDefault(),"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms%1000)/10));}
        if(page==2&&timerRunning){long remain=timerEnd-System.currentTimeMillis();if(remain<=0){timerRunning=false;timerDuration=0;toolTime.setText("00:00");timerStartButton.setText("START");try{((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(700);}catch(Exception ignored){}
            if(toneGenerator!=null)toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,3000);
            new AlertDialog.Builder(this).setTitle("Timer finished").setMessage("Time is up!").setPositiveButton("OK",null).show();}else{timerDuration=remain;toolTime.setText(format(remain));}}
    }
    String format(long ms){long totalSeconds=Math.max(0,ms)/1000;return String.format(Locale.getDefault(),"%02d:%02d",totalSeconds/60,totalSeconds%60);}
    void savePresets(){SharedPreferences.Editor ed=prefs.edit();for(int i=0;i<3;i++)ed.putInt("preset_"+i,presets[i]);ed.apply();}
    void editTimerDuration(){
        LinearLayout fields=row();EditText minutes=new EditText(this),seconds=new EditText(this);
        minutes.setSingleLine(true);minutes.setInputType(2);minutes.setHint("Minutes");minutes.setText("");
        seconds.setSingleLine(true);seconds.setInputType(2);seconds.setHint("Seconds");seconds.setText("");
        fields.addView(minutes,new LinearLayout.LayoutParams(0,dp(60),1));fields.addView(text(":",24,true));fields.addView(seconds,new LinearLayout.LayoutParams(0,dp(60),1));
        new AlertDialog.Builder(this).setTitle("Set timer (minutes : seconds)").setView(fields).setPositiveButton("Set",(d,w)->{try{long m=Long.parseLong(minutes.getText().toString().trim());long sec=Long.parseLong(seconds.getText().toString().trim());if(sec<0||sec>59||m<0)throw new NumberFormatException();timerDuration=(m*60+sec)*1000;timerRunning=false;timerStartButton.setText("START");toolTime.setText(format(timerDuration));}catch(Exception ex){Toast.makeText(this,"Enter valid minutes and seconds (0–59 seconds)",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancel",null).show();
    }
    void editPreset(int i){
        LinearLayout fields=row();EditText minutes=new EditText(this),seconds=new EditText(this);
        minutes.setSingleLine(true);minutes.setInputType(2);minutes.setHint("Minutes");minutes.setText("");
        seconds.setSingleLine(true);seconds.setInputType(2);seconds.setHint("Seconds");seconds.setText("");
        fields.addView(minutes,new LinearLayout.LayoutParams(0,dp(60),1));fields.addView(text(":",24,true));fields.addView(seconds,new LinearLayout.LayoutParams(0,dp(60),1));
        new AlertDialog.Builder(this).setTitle("Edit preset (minutes : seconds)").setView(fields).setPositiveButton("Save",(d,w)->{try{long m=Long.parseLong(minutes.getText().toString().trim());long sec=Long.parseLong(seconds.getText().toString().trim());if(m<0||sec<0||sec>59)throw new NumberFormatException();presets[i]=(int)Math.min(Integer.MAX_VALUE,(m*60+sec)*1000);savePresets();buildTimer();}catch(Exception ignored){Toast.makeText(this,"Enter valid minutes and seconds (0–59 seconds)",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancel",null).show();
    }
    void showOptions(){
        String[] opts={"Light","Dark"};
        new AlertDialog.Builder(this).setTitle("Clock theme").setItems(opts,(d,w)->{
            if(w==0)showLightColors();
            else showDarkColors();
        }).show();
    }
    void toggleManualNightMode(){
        WindowManager.LayoutParams lp=getWindow().getAttributes();
        if(!night){
            brightnessBeforeNight=lp.screenBrightness;
            night=true;
            bg=Color.BLACK;fg=Color.WHITE;accent=0xFF777777;
            lp.screenBrightness=brightnessBeforeNight<0f?0.12f:Math.max(0.05f,brightnessBeforeNight*0.25f);
        }else{
            night=false;
            bg=originalBg;fg=originalFg;accent=originalAccent;
            if(brightnessBeforeNight>=0f)lp.screenBrightness=brightnessBeforeNight;
            else lp.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
        getWindow().setAttributes(lp);
        showPage(page);
    }
    int blendSeparator(int background,int foreground){return 0xFF666666;}
    void showColors(){
        String[] categories={"Dark colour combinations","Light colour combinations"};
        new AlertDialog.Builder(this).setTitle("Clock theme").setItems(categories,(d,w)->{
            if(w==0)showDarkColors();else showLightColors();
        }).show();
    }
    void applyColor(int b,int f,int a){
        bg=b;fg=f;accent=a;originalBg=bg;originalFg=fg;originalAccent=accent;
        prefs.edit().putInt("theme_bg",bg).putInt("theme_fg",fg).putInt("theme_accent",accent).apply();
        showPage(page);
    }
    void showDarkColors(){
        String[] names={"Midnight Mint","Pure Black & White","Navy Cyan","Charcoal Amber","OLED Purple","Forest Green","Burgundy Rose","Ocean Blue","Slate Orange","Dark Teal","Graphite Lime","Deep Red","Lavender Night","Emerald Black","Blue Steel","Sunset","Ice Blue"};
        int[][] colors={
            {0xFF101820,0xFFFFFFFF,0xFF8DE4CB},{0xFF000000,0xFFFFFFFF,0xFFFFFFFF},{0xFF050F23,0xFFFFFFFF,0xFF00FFFF},
            {0xFF1C1C1C,0xFFFFFFFF,0xFFFFBE46},{0xFF160D25,0xFFF7EFFF,0xFFC49BFF},{0xFF071B13,0xFFE5FFE8,0xFF64D995},
            {0xFF260D16,0xFFFFE8EF,0xFFFF729F},{0xFF071D2B,0xFFE8F7FF,0xFF4DC9FF},{0xFF24201A,0xFFFFF3E1,0xFFFF9D4D},
            {0xFF062321,0xFFE1FFFB,0xFF3CE0C2},{0xFF171B17,0xFFF1FFE7,0xFFB4F75C},{0xFF260707,0xFFFFEEEE,0xFFFF5B5B},
            {0xFF19172A,0xFFF0EEFF,0xFFB6A9FF},{0xFF001B12,0xFFE1FFF2,0xFF00D890},
            {0xFF142333,0xFFE9F2FF,0xFF8AB4F8},{0xFF28130C,0xFFFFE9DA,0xFFFF9568},{0xFF0D1F2D,0xFFE9FAFF,0xFF7CE4FF}
        };
        new AlertDialog.Builder(this).setTitle("Dark colour combinations").setItems(names,(d,w)->applyColor(colors[w][0],colors[w][1],colors[w][2])).show();
    }
    void showLightColors(){
        String[] names={"Paper White","Warm Cream","Soft Sage","Sky Blue","Rose Quartz","Lilac Morning","Sand & Cocoa","Mint Frost","Silver Cloud","Peach Light"};
        int[][] colors={
            {0xFFFFFFFF,0xFF161616,0xFF3D6DCC},{0xFFFFF3D9,0xFF292115,0xFF9C5D20},
            {0xFFEAF4E8,0xFF1E3324,0xFF4C8C59},{0xFFEAF6FF,0xFF102B46,0xFF247CC1},
            {0xFFFFEEF2,0xFF46212B,0xFFC34D70},{0xFFF2EDFF,0xFF30244C,0xFF8060C5},
            {0xFFF4EBDD,0xFF3D3025,0xFF987044},{0xFFE7FAF5,0xFF183B34,0xFF2D9B83},
            {0xFFF0F2F5,0xFF242A33,0xFF697D98},{0xFFFFF0E7,0xFF472C21,0xFFD8784D}
        };
        new AlertDialog.Builder(this).setTitle("Light colour combinations").setItems(names,(d,w)->applyColor(colors[w][0],colors[w][1],colors[w][2])).show();
    }
    @Override public void onBackPressed(){if(page!=0)showPage(0);else showOptions();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(toneGenerator!=null){toneGenerator.stopTone();toneGenerator.release();}super.onDestroy();}
}
