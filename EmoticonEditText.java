package com.leicher.whofearwho.widget.EmoticonEditText;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmoticonEditText extends android.support.v7.widget.AppCompatEditText implements TextWatcher{

    // 头像 的 正则表达式
    public static final String EMOTICON_PATTERN = "";

    private boolean deleting = false;
    private Object deleteTempSpan;
    private boolean selecting = false;
    private Class spanClass;


    public EmoticonEditText(Context context) {
        super(context);
        init(context, null, 0);
    }

    public EmoticonEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public EmoticonEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr){
        addTextChangedListener(this);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        switch (id){
            case android.R.id.paste: // 粘贴处理

                int min = 0;
                int max = getText().length();

                if (isFocused()) {
                    final int selStart = getSelectionStart();
                    final int selEnd = getSelectionEnd();

                    min = Math.max(0, Math.min(selStart, selEnd));
                    max = Math.max(0, Math.max(selStart, selEnd));
                }
                paste(min, max);
                return true;

        }
        return super.onTextContextMenuItem(id);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selecting){
            selecting = false;
            return;
        }
        if (selStart == selEnd) return;

        Editable text = getText();
        Object[] spans = text.getSpans(selStart, selEnd, spanClass);
        int min = selStart;
        int max = selEnd;

        if (spans != null && spans.length > 0){
            for (Object span : spans) {

                min = Math.min(min, text.getSpanStart(span));
                max = Math.max(max, text.getSpanEnd(span));
            }
            if (min < max && (min != selStart || max != selEnd)) {
                selecting = true;
                Selection.setSelection(text, min, max);
            }
        }
    }


    private void paste(int min, int max) {
        int maxLength = getMaxLength();
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            boolean didFirst = false;
            Editable mText = getText();
            for (int i = 0; i < clip.getItemCount(); i++) {
                CharSequence paste;
                // Get an item as text and remove all spans by toString().
                final CharSequence text = clip.getItemAt(i).coerceToText(getContext());
                paste = (text instanceof Spanned) ? text.toString() : text;
                paste = addEmoticonSpanAndRemoveEnd(maxLength - mText.length(), paste, this);
                if (paste != null && paste.length() > 0) {
                    if (!didFirst) {
                        Selection.setSelection((Spannable) mText, max);
                        ((Editable) mText).replace(min, max, paste);
                        didFirst = true;
                    } else {
                        ((Editable) mText).insert(getSelectionEnd(), "\n");
                        ((Editable) mText).insert(getSelectionEnd(), paste);
                    }
                }
            }
        }
    }

    public int getMaxLength(){
        int maxLength = Integer.MAX_VALUE;
        InputFilter[] filters = getFilters();
        if (filters != null) {
            for (InputFilter filter : filters) {
                if (filter instanceof InputFilter.LengthFilter) {
                    maxLength = Math.min(maxLength, classToMax((InputFilter.LengthFilter) filter));
                }
            }
        }
        return maxLength;
    }

    private int classToMax(InputFilter.LengthFilter filter){
        if (Build.VERSION.SDK_INT >= 21) return filter.getMax();
        int max = Integer.MAX_VALUE;
        try {
            Field mMax = InputFilter.LengthFilter.class.getDeclaredField("mMax");
            mMax.setAccessible(true);
            max = (int) mMax.get(filter);
        } catch (Throwable e){
            e.printStackTrace();
        }
        return max;
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (deleting) return;
        if (count > 0 && s instanceof Editable){
            Editable editable = (Editable) s;
            Object[] spans = editable.getSpans(start, start + count, spanClass);
            if (spans != null && spans.length > 0){
                deleteTempSpan = spans[0];
            }
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (deleting) return;
        // do nothing
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (deleting){
            deleting = false;
            return;
        }
        if (deleteTempSpan != null){
            int spanStart = editable.getSpanStart(deleteTempSpan);
            int spanEnd = editable.getSpanEnd(deleteTempSpan);
            if (spanStart >= 0 && spanEnd >= 0 && spanStart != spanEnd){
                deleting = true;
                editable.delete(spanStart, spanEnd);
            }else {
                deleting = false;
            }
            deleteTempSpan = null;
        }
    }


    private static CharSequence addEmoticonSpanAndRemoveEnd(int surplus, CharSequence sequence, EmoticonEditText editText){
        if (TextUtils.isEmpty(sequence)) return sequence;
        SpannableStringBuilder result = new SpannableStringBuilder();
        String value = sequence.toString();

        Pattern p = Pattern.compile(EMOTICON_PATTERN);
        Matcher m = p.matcher(value);
        int start = 0;

        while (m.find()){
            if (m.end() > surplus){
                start = m.end();
                break;
            }
            result.append(value.substring(start, m.start()));
            start = m.end();
            String group = m.group();
            SpannableString ss = new SpannableString(group);

            Object span = editText.newSpan();

            ss.setSpan(span, 0, group.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.append(ss);
        }

        if (start < surplus) {
            result.append(value.substring(start, Math.min(value.length(), surplus)));
        }

        return result;

    }


    public Object newSpan(){
        try {
            return spanClass != null ? spanClass.newInstance() : null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

}
