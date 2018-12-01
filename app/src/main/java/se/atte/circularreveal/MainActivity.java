package se.atte.circularreveal;

import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.main).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager fm = getSupportFragmentManager();
                CircularRevealFragment.getInstance(fm)
                        .setTouchPoint(TouchPointManager.getInstance().getPoint())
                        .setDuration(4000)
                        .setRevealColor(getResources().getColor(R.color.colorAccent, getTheme()))
                        .setTransitFragment(new MainActivityFragment())
                        .startCircularReveal(R.id.main, fm);
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int)ev.getRawX(), (int)ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count > 0) {
            getFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
