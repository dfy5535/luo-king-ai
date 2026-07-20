package com.luoking.agent

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "洛克王国AI助手"
        tv.textSize = 24f
        setContentView(tv)
    }
}