package com.android.example.flips;

import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.Interpolator;

public class AccelerateDecelerateInterpolator implements Interpolator, TimeInterpolator {
    public AccelerateDecelerateInterpolator() {
    }
    
    public AccelerateDecelerateInterpolator(Context context, AttributeSet attrs) {
    }
    
    public float getInterpolation(float input) {
        return (float)(Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
    }
}