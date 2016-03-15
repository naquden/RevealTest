/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atte.testapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.Point;
import android.os.Bundle;
import android.transition.Fade;
import android.transition.Transition;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
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

    public CircularRevealFragment setRevealColor(int color) {
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

    public void startCircularReveal(int containerId, Activity activity) {
        final FragmentManager fm = activity.getFragmentManager();
        if (fm.findFragmentByTag(TAG) == null) {
            mContainerId = containerId;
            mDuration = activity.getResources().getInteger(android.R.integer.config_mediumAnimTime);
            fm.beginTransaction().add(containerId, this, TAG).commitAllowingStateLoss();
        } else {
            Log.w(TAG, "An instance of CircularRevealFragment already exists");
        }
    }

    public static void endCircularReveal(int containerId, final FragmentManager fm,
            Fragment transitToFragment) {
        if (transitToFragment != null) {
            // Create enter transition to the entering fragment if necessary.
            Transition transition = transitToFragment.getEnterTransition();
            if (transition == null) {
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

    /**
     * Empty constructor used only by the {@link FragmentManager}.
     */
    public CircularRevealFragment() {
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.outgoing_call_animation, container, false);
    }

    public void startOutgoingAnimation() {
        final Activity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "Asked to do outgoing call animation when not attached");
            return;
        }

        final View view = activity.getWindow().getDecorView();

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

        view.findViewById(R.id.outgoing_call_animation_circle)
                .setBackgroundColor(mRevealColor != 0 ? mRevealColor :
                        getThemeColor(activity, android.R.attr.colorPrimary));

        view.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                final ViewTreeObserver vto = view.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnPreDrawListener(this);
                }
                final Animator animator = getRevealAnimator(mTouchPoint, mDuration);
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

    private Animator getRevealAnimator(Point touchPoint, int duration) {
        final Activity activity = getActivity();
        final View view = activity.getWindow().getDecorView();
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        int startX = size.x / 2;
        int startY = size.y / 2;
        if (touchPoint != null) {
            startX = touchPoint.x;
            startY = touchPoint.y;
        }

        final Animator valueAnimator = ViewAnimationUtils.createCircularReveal(view, startX, startY,
                0, Math.max(size.x, size.y));
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
