/*
 * Copyright (C) Atte.
 */

package se.atte.circularreveal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.transition.Fade;
import android.transition.Transition;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;

import java.util.ArrayList;
import java.util.List;

public class CircularRevealFragment extends Fragment {
    static final String TAG = "CircularRevealFragment";

    private boolean mAnimationStarted;

    private int mContainerId;
    private int mRevealColor;
    private Point mTouchPoint;
    private Fragment mTransitToFragment;
    private List<OnCircularRevealCompleteListener> mListeners = new ArrayList<>();
    private int mDuration;

    interface OnCircularRevealCompleteListener {
        void onCircularRevealComplete(FragmentManager fm);
    }

    public static CircularRevealFragment getInstance(FragmentManager fm) {
        Fragment fragment = fm.findFragmentByTag(TAG);
        return fragment != null ? (CircularRevealFragment) fragment : new CircularRevealFragment();
    }

    public CircularRevealFragment setRevealColor(@ColorInt int color) {
        mRevealColor = color;
        return this;
    }

    public CircularRevealFragment setTouchPoint(Point touchPoint) {
        mTouchPoint = touchPoint;
        return this;
    }

    public CircularRevealFragment addListener(OnCircularRevealCompleteListener listener) {
        mListeners.add(listener);
        return this;
    }

    public CircularRevealFragment setTransitFragment(Fragment fragment) {
        mTransitToFragment = fragment;
        return this;
    }

    public CircularRevealFragment setDuration(int duration) {
        mDuration = duration;
        return this;
    }

    public void startCircularReveal(int containerId, @NonNull FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) {
            mContainerId = containerId;
            fm.beginTransaction().add(containerId, this, TAG).commitAllowingStateLoss();
        } else {
            Log.w(TAG, "An instance of CircularRevealFragment already exists");
        }
    }

    public static void endCircularReveal(int containerId, final FragmentManager fm,
                                         Fragment transitToFragment) {
        if (transitToFragment != null) {
            // Create enter transition to the entering fragment if necessary.
            Object transObject = transitToFragment.getEnterTransition();
            Transition transition;
            if (transObject instanceof Transition) {
                transition = (Transition) transObject;
            } else {
                transition = new Fade();
                transitToFragment.setEnterTransition(transition);
            }

            // Remove reveal fragment when new fragment has finished it's transition
            transition.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    removeCircularRevealFragment(fm);
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    removeCircularRevealFragment(fm);
                }

                @Override
                public void onTransitionPause(Transition transition) {
                }

                @Override
                public void onTransitionResume(Transition transition) {
                }
            });

            fm.beginTransaction().add(containerId, transitToFragment)
                    .addToBackStack(transitToFragment.getClass().getName()).commit();
        } else {
            removeCircularRevealFragment(fm);
        }
    }

    private static void removeCircularRevealFragment(FragmentManager fm) {
        final Fragment fragment = fm.findFragmentByTag(TAG);
        if (fragment != null) {
            fm.beginTransaction().remove(fragment).commitAllowingStateLoss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mAnimationStarted) {
            // Only run the animation once for each instance of the fragment
            startOutgoingAnimation();
        }
        mAnimationStarted = true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = new View(container.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    public void startOutgoingAnimation() {
        final Context context = getContext();
        final View view = getView();
        if (context == null || view == null || isDetached()) {
            Log.w(TAG, "Asked to do outgoing call animation when not attached");
            return;
        }

        // The circle starts from an initial size of 0 so clip it such that it
        // is invisible.
        // Otherwise the first frame is drawn with a fully opaque screen which
        // causes jank. When
        // the animation later starts, this clip will be clobbered by the
        // circular reveal clip.
        // See ViewAnimationUtils.createCircularReveal.
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Using (0, 0, 0, 0) will not work since the outline will
                // simply be treated as
                // an empty outline.
                outline.setOval(-1, -1, 0, 0);
            }
        });
        view.setClipToOutline(true);

        view.setBackgroundColor(mRevealColor != 0 ?
                mRevealColor : getThemeColor(context, android.R.attr.colorPrimary));

        view.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                final ViewTreeObserver vto = view.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnPreDrawListener(this);
                }
                final Animator animator = getRevealAnimator(view, mTouchPoint, mDuration);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setClipToOutline(false);
                        for (OnCircularRevealCompleteListener listener : mListeners) {
                            listener.onCircularRevealComplete(getFragmentManager());
                        }
                        endCircularReveal(mContainerId, getFragmentManager(), mTransitToFragment);
                    }
                });
                animator.start();
                return false;
            }
        });
    }

    private Animator getRevealAnimator(View view, Point touchPoint, int duration) {
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        int startX;
        int startY;
        if (touchPoint != null) {
            int[] rect = new int[2];
            view.getLocationOnScreen(rect);
            // touch is global location, compensate view' location
            startX = touchPoint.x - rect[0];
            startY = touchPoint.y - rect[1];
        } else {
            startX = width / 2;
            startY = height / 2;
        }

        final Animator valueAnimator = ViewAnimationUtils.createCircularReveal(
                view, startX, startY, 0, Math.max(width, height));
        valueAnimator.setDuration(duration);
        return valueAnimator;
    }

    private static int getThemeColor(Context context, int colorId) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{colorId});
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

}
