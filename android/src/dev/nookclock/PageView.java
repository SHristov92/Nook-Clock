package dev.nookclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the current page scaled from its logical 800x600 / 600x800 canvas,
 * and turns touches into swipe / tap / long-press callbacks.
 */
final class PageView extends View {
    interface Listener {
        void onSwipe(int dir);      // +1 = next page, -1 = previous
        void onTap();
        void onLongPress();
    }

    final Page.Ctx ctx = new Page.Ctx();
    Page page;
    Listener listener;

    private float downX, downY;
    private long downAt;

    PageView(Context c) {
        super(c);
    }

    @Override
    protected void onDraw(Canvas c) {
        int vw = getWidth(), vh = getHeight();
        ctx.land = vw > vh;
        ctx.W = ctx.land ? 800 : 600;
        ctx.H = ctx.land ? 600 : 800;
        float k = Math.min(vw / (float) ctx.W, vh / (float) ctx.H);
        c.drawColor(Color.WHITE);
        if (page == null)
            return;
        c.save();
        c.translate((vw - ctx.W * k) / 2, (vh - ctx.H * k) / 2);
        c.scale(k, k);
        page.draw(c, ctx);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
            downX = e.getX();
            downY = e.getY();
            downAt = e.getEventTime();
            return true;
        case MotionEvent.ACTION_UP:
            if (listener == null)
                return true;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (Math.abs(dx) > 80 && Math.abs(dx) > Math.abs(dy) * 1.5f)
                listener.onSwipe(dx < 0 ? 1 : -1);
            else if (Math.abs(dx) < 40 && Math.abs(dy) < 40)
                if (e.getEventTime() - downAt > 800)
                    listener.onLongPress();
                else
                    listener.onTap();
            return true;
        }
        return true;
    }
}
