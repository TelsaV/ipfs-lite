package threads.server.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.ipfs.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.peers.PEERS;

public class NameDialogFragment extends DialogFragment {
    public static final String TAG = NameDialogFragment.class.getSimpleName();

    private final AtomicBoolean notPrintErrorMessages = new AtomicBoolean(false);

    private long mLastClickTime = 0;
    private TextInputLayout mEditNameLayout;
    private TextInputEditText mEditName;
    private Context mContext;

    public static NameDialogFragment newInstance(@NonNull String pid, @NonNull String title) {

        Bundle bundle = new Bundle();
        bundle.putString(Content.TITLE, title);
        bundle.putString(Content.PID, pid);

        NameDialogFragment fragment = new NameDialogFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        Bundle args = getArguments();
        Objects.requireNonNull(args);
        final String pid = args.getString(Content.PID);
        Objects.requireNonNull(pid);
        String title = args.getString(Content.TITLE);


        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.name_view, null);
        mEditNameLayout = view.findViewById(R.id.edit_name_layout);
        mEditNameLayout.setCounterEnabled(true);
        mEditNameLayout.setCounterMaxLength(30);

        mEditName = view.findViewById(R.id.edit_name);
        InputFilter[] filterTitle = new InputFilter[1];
        filterTitle[0] = new InputFilter.LengthFilter(30);
        mEditName.setFilters(filterTitle);

        mEditName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                isValidName(getDialog());
            }
        });


        builder.setView(view)
                // Add action buttons
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }

                    mLastClickTime = SystemClock.elapsedRealtime();

                    removeKeyboards();


                    Editable text = mEditName.getText();
                    Objects.requireNonNull(text);
                    String name = text.toString();

                    name(pid, name);


                })
                .setTitle(title);


        Dialog dialog = builder.create();

        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }


        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        return dialog;
    }


    private void name(@NonNull String pid, @NonNull String name) {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                PEERS.getInstance(mContext).setUserAlias(pid, name);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            } finally {
                dismiss();
            }
        });


    }

    private void isValidName(Dialog dialog) {
        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            Editable text = mEditName.getText();
            Objects.requireNonNull(text);
            String multi = text.toString();


            boolean result = !multi.isEmpty();


            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(result);


            if (!notPrintErrorMessages.get()) {

                if (multi.isEmpty()) {
                    mEditNameLayout.setError(getString(R.string.name_not_valid));
                } else {
                    mEditNameLayout.setError(null);
                }

            } else {
                mEditNameLayout.setError(null);
            }
        }
    }


    private void removeKeyboards() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mEditName.getWindowToken(), 0);
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        removeKeyboards();
    }

    @Override
    public void onResume() {
        super.onResume();
        notPrintErrorMessages.set(true);
        isValidName(getDialog());
        notPrintErrorMessages.set(false);
    }

}
