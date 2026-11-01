package com.tarsem.customerlist;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.ListView;


public class SwipeListView extends ListView {

    private final int slop;
    private final float binW;
    private float downX, downY, startTx;
    private int downPos = -1;
    private View card;
    private View openCard;
    private boolean swiping = false;
    private OnDeleteListener deleteListener;

    public interface OnDeleteListener { void onDelete(int position); }

    public void setOnDeleteListener(OnDeleteListener l) { deleteListener = l; }

    public SwipeListView(Context c) { this(c, null); }

    public SwipeListView(Context c, AttributeSet a) { this(c, a, android.R.attr.listViewStyle); }

    public SwipeListView(Context c, AttributeSet a, int style) {
        super(c, a, style);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        binW = 84 * c.getResources().getDisplayMetrics().density;
        setOnScrollListener(new OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int state) {
                if (state != SCROLL_STATE_IDLE) closeOpenRow();
            }
            @Override public void onScroll(AbsListView v, int first, int count, int total) { }
        });
    }

    private View rowCard(int pos) {
        View row = getChildAt(pos - getFirstVisiblePosition());
        return row == null ? null : row.findViewById(R.id.card);
    }

    private void showBin(View c) {
        View b = ((View) c.getParent()).findViewById(R.id.binLayer);
        if (b != null) b.setVisibility(View.VISIBLE);
    }

    private void hideBin(View c) {
        View b = ((View) c.getParent()).findViewById(R.id.binLayer);
        if (b != null) b.setVisibility(View.INVISIBLE);
    }

    private void settle(final View c, final float target) {
        c.animate().translationX(target).setDuration(150).withEndAction(() -> {
            if (target == 0f) hideBin(c);
        }).start();
    }

    public boolean closeOpenRow() {
        if (openCard == null) return false;
        settle(openCard, 0f);
        openCard = null;
        return true;
    }

    public void resetOpen() {
        openCard = null;
        swiping = false;
    }

    private void onDown(MotionEvent ev) {
        downX = ev.getX();
        downY = ev.getY();
        downPos = pointToPosition((int) downX, (int) downY);
        card = downPos >= 0 ? rowCard(downPos) : null;
        startTx = card != null ? card.getTranslationX() : 0f;
        swiping = false;
    }

    private void checkStart(MotionEvent ev) {
        if (swiping || card == null) return;
        float dx = ev.getX() - downX, dy = ev.getY() - downY;
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            swiping = true;
            if (openCard != null && openCard != card) {
                settle(openCard, 0f);
                openCard = null;
            }
            showBin(card);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel);
            cancel.recycle();
        }
    }

    private void finishSwipe() {
        swiping = false;
        if (card == null) return;
        float tx = card.getTranslationX();
        float w = card.getWidth();
        if (deleteListener != null && downPos >= 0 && w > 0 && Math.abs(tx) > w * 0.55f) {
            final int pos = downPos;
            card.animate().translationX(tx > 0 ? w : -w).setDuration(150)
                    .withEndAction(() -> deleteListener.onDelete(pos)).start();
            openCard = null;
            return;
        }
        if (Math.abs(tx) > binW * 0.45f) {
            settle(card, tx > 0 ? binW : -binW);
            openCard = card;
        } else {
            settle(card, 0f);
            if (openCard == card) openCard = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) onDown(ev);
        else if (a == MotionEvent.ACTION_MOVE) checkStart(ev);
        if (swiping) return true;
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) onDown(ev);
        else if (a == MotionEvent.ACTION_MOVE) checkStart(ev);
        if (swiping) {
            if (a == MotionEvent.ACTION_MOVE && card != null) {
                float tx = startTx + (ev.getX() - downX);
                float lim = card.getWidth() > 0 ? card.getWidth() : binW * 1.2f;
                card.setTranslationX(Math.max(-lim, Math.min(lim, tx)));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                finishSwipe();
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
