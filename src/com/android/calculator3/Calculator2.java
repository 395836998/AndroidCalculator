/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.calculator3;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;


/**
 * 计算器主Activity
 *
 */
public class Calculator2 extends Activity implements PanelSwitcher.Listener, Logic.Listener,
        OnClickListener, OnMenuItemClickListener {
	
	//自定义的按键、点击、长点击事件监听
    EventListener mListener = new EventListener();
    //自定义的视图切换动画效果组件
    private CalculatorDisplay mDisplay;
    //持久化对象
    private Persist mPersist;
    //历史对象
    private History mHistory;
    //业务逻辑对象
    private Logic mLogic;
    //页面动画切换对象
    private ViewPager mPager;
    
    //清除按钮
    private View mClearButton;
    //删除按钮
    private View mBackspaceButton;
    //菜单按钮
    private View mOverflowMenuButton;

    //两个面板的常量
    static final int BASIC_PANEL    = 0;
    static final int ADVANCED_PANEL = 1;

    private static final String LOG_TAG = "Calculator";
    private static final boolean LOG_ENABLED = false;
    private static final String STATE_CURRENT_VIEW = "state-current-view";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Disable IME for this application（禁用软键盘功能，即输入框等点击后不会弹出输入软键盘）
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.main); 
        
        
        //计算器的按钮区由两个组件切换实现（基本面板和高级面板，基本面板包括数字加减乘除，高级面板包括sin、cos等科学计算）
        //这个ViewPager用来动态切换这两个面板
        mPager = (ViewPager) findViewById(R.id.panelswitch);
        if (mPager != null) {
            mPager.setAdapter(new PageAdapter(mPager));
        } else {
            // Single page UI
            final TypedArray buttons = getResources().obtainTypedArray(R.array.buttons);
            for (int i = 0; i < buttons.length(); i++) {
                setOnClickListener(null, buttons.getResourceId(i, 0));
            }
            buttons.recycle();
        }

        if (mClearButton == null) {
            mClearButton = findViewById(R.id.clear);
            mClearButton.setOnClickListener(mListener);
            mClearButton.setOnLongClickListener(mListener);
        }
        if (mBackspaceButton == null) {
            mBackspaceButton = findViewById(R.id.del);
            mBackspaceButton.setOnClickListener(mListener);
            mBackspaceButton.setOnLongClickListener(mListener);
        }

        //初始化存储对象
        mPersist = new Persist(this);
        mPersist.load();

        //获取历史记录
        mHistory = mPersist.history;

        mDisplay = (CalculatorDisplay) findViewById(R.id.display);

        //初始化逻辑控制对象
        mLogic = new Logic(this, mHistory, mDisplay);
        mLogic.setListener(this);

        //初始化删除模式（是清除按钮还是回退删除按钮）
        mLogic.setDeleteMode(mPersist.getDeleteMode());
        mLogic.setLineLength(mDisplay.getMaxDigits());

        //历史数据适配器
        HistoryAdapter historyAdapter = new HistoryAdapter(this, mHistory, mLogic);
        mHistory.setObserver(historyAdapter);

        //设置输入区主面板
        if (mPager != null) {
            mPager.setCurrentItem(state == null ? 0 : state.getInt(STATE_CURRENT_VIEW, 0));
        }

        //设置事件监听器的处理对象
        mListener.setHandler(mLogic, mPager);
        //设置计算器显示区的事件监听器
        mDisplay.setOnKeyListener(mListener);

        if (!ViewConfiguration.get(this).hasPermanentMenuKey()) {
        	//创建菜单
            createFakeMenu();
        }

        //获取历史记录
        mLogic.resumeWithHistory();
        //确定显示清除按钮还是删除按钮
        updateDeleteMode();
    }

    private void updateDeleteMode() {
    	//切换清除和删除按钮的显示效果
        if (mLogic.getDeleteMode() == Logic.DELETE_MODE_BACKSPACE) {
            mClearButton.setVisibility(View.GONE);
            mBackspaceButton.setVisibility(View.VISIBLE);
        } else {
            mClearButton.setVisibility(View.VISIBLE);
            mBackspaceButton.setVisibility(View.GONE);
        }
    }

    void setOnClickListener(View root, int id) {
        final View target = root != null ? root.findViewById(id) : findViewById(id);
        target.setOnClickListener(mListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.basic).setVisible(!getBasicVisibility());
        menu.findItem(R.id.advanced).setVisible(!getAdvancedVisibility());
        return true;
    }


    private void createFakeMenu() {
        mOverflowMenuButton = findViewById(R.id.overflow_menu);
        if (mOverflowMenuButton != null) {
            mOverflowMenuButton.setVisibility(View.VISIBLE);
            mOverflowMenuButton.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.overflow_menu:
                PopupMenu menu = constructPopupMenu();
                if (menu != null) {
                    menu.show();
                }
                break;
        }
    }

    private PopupMenu constructPopupMenu() {
        final PopupMenu popupMenu = new PopupMenu(this, mOverflowMenuButton);
        mOverflowMenuButton.setOnTouchListener(popupMenu.getDragToOpenListener());
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.menu);
        popupMenu.setOnMenuItemClickListener(this);
        onPrepareOptionsMenu(menu);
        return popupMenu;
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    private boolean getBasicVisibility() {
        return mPager != null && mPager.getCurrentItem() == BASIC_PANEL;
    }

    private boolean getAdvancedVisibility() {
        return mPager != null && mPager.getCurrentItem() == ADVANCED_PANEL;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_history:
                mHistory.clear();
                mLogic.onClear();
                break;

            case R.id.basic:
                if (!getBasicVisibility() && mPager != null) {
                    mPager.setCurrentItem(BASIC_PANEL, true);
                }
                break;

            case R.id.advanced:
                if (!getAdvancedVisibility() && mPager != null) {
                    mPager.setCurrentItem(ADVANCED_PANEL, true);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mPager != null) {
            state.putInt(STATE_CURRENT_VIEW, mPager.getCurrentItem());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mLogic.updateHistory();
        mPersist.setDeleteMode(mLogic.getDeleteMode());
        mPersist.save();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_BACK && getAdvancedVisibility()
                && mPager != null) {
            mPager.setCurrentItem(BASIC_PANEL);
            return true;
        } else {
            return super.onKeyDown(keyCode, keyEvent);
        }
    }

    static void log(String message) {
        if (LOG_ENABLED) {
            Log.v(LOG_TAG, message);
        }
    }

    @Override
    public void onChange() {
        invalidateOptionsMenu();
    }

    @Override
    public void onDeleteModeChange() {
    	//当Logic类中触发删除模式的变更时，更新按钮的显示
        updateDeleteMode();
    }

    
    /**
     * 页面切换适配器
     *
     */
    class PageAdapter extends PagerAdapter {
        private View mSimplePage;
        private View mAdvancedPage;

        public PageAdapter(ViewPager parent) {
            //根据父组件获取到布局加载器
        	final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            
        	//加载基本面板和高级面板布局
            final View simplePage = inflater.inflate(R.layout.simple_pad, parent, false);
            final View advancedPage = inflater.inflate(R.layout.advanced_pad, parent, false);
            mSimplePage = simplePage;
            mAdvancedPage = advancedPage;

            //获取基本面板的按钮并设置监听器
            final Resources res = getResources();
            final TypedArray simpleButtons = res.obtainTypedArray(R.array.simple_buttons);
            for (int i = 0; i < simpleButtons.length(); i++) {
                setOnClickListener(simplePage, simpleButtons.getResourceId(i, 0));
            }
            //TODO
            simpleButtons.recycle();

            //获取高级面板的按钮并设置监听器
            final TypedArray advancedButtons = res.obtainTypedArray(R.array.advanced_buttons);
            for (int i = 0; i < advancedButtons.length(); i++) {
                setOnClickListener(advancedPage, advancedButtons.getResourceId(i, 0));
            }
            advancedButtons.recycle();

            //基本面板的清除按钮
            final View clearButton = simplePage.findViewById(R.id.clear);
            if (clearButton != null) {
                mClearButton = clearButton;
            }

            //删除按钮
            final View backspaceButton = simplePage.findViewById(R.id.del);
            if (backspaceButton != null) {
                mBackspaceButton = backspaceButton;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public void startUpdate(View container) {
        }

        @Override
        public Object instantiateItem(View container, int position) {
            final View page = position == 0 ? mSimplePage : mAdvancedPage;
            ((ViewGroup) container).addView(page);
            return page;
        }

        @Override
        public void destroyItem(View container, int position, Object object) {
            ((ViewGroup) container).removeView((View) object);
        }

        @Override
        public void finishUpdate(View container) {
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
        }
    }
}
