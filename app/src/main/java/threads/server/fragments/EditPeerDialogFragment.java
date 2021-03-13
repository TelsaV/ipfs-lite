package threads.server.fragments;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.events.EVENTS;
import io.ipfs.IPFS;
import threads.server.services.LiteService;


public class EditPeerDialogFragment extends BottomSheetDialogFragment {
    public static final String TAG = EditPeerDialogFragment.class.getSimpleName();
    private final AtomicBoolean issueMessage = new AtomicBoolean(false);
    private long mLastClickTime = 0;
    private TextInputLayout mEditAccountLayout;
    private TextInputEditText mMultihash;
    private final ActivityResultLauncher<Intent> mScanRequestForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    IntentResult resultIntent = IntentIntegrator.parseActivityResult(
                            IntentIntegrator.REQUEST_CODE, result.getResultCode(), result.getData());
                    if (resultIntent != null) {
                        if (resultIntent.getContents() != null) {
                            String content = resultIntent.getContents();
                            mMultihash.setText(content);
                        }
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            });
    private TextInputEditText mAddress;
    private Context mContext;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    invokeScan();
                } else {
                    EVENTS.getInstance(mContext).permission(
                            getString(R.string.permission_camera_denied));
                }
            });
    private TextInputEditText mName;
    private boolean hasCamera;

    public static EditPeerDialogFragment newInstance() {
        return new EditPeerDialogFragment();
    }

    public static EditPeerDialogFragment newInstance(@NonNull String pid, @Nullable String address) {
        Bundle bundle = new Bundle();
        bundle.putString(Content.PID, pid);
        if (address != null) {
            bundle.putString(Content.ADDR, address);
        }
        EditPeerDialogFragment fragment = new EditPeerDialogFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        PackageManager pm = mContext.getPackageManager();
        hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @NonNull
    private String getValidPeerID(@Nullable String multi) {

        String multihash = "";

        if (multi != null && !multi.isEmpty()) {

            try {

                multi = multi.trim();

                String style = "/p2p/";
                if (multi.contains(style)) {
                    int index = multi.indexOf(style);
                    multi = multi.substring(index + style.length());
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }


            return IPFS.getInstance(mContext).decodeName(multi);

        }

        return multihash;

    }

    private boolean isValidPeerID() {

        Editable text = mMultihash.getText();
        Objects.requireNonNull(text);
        String multi = text.toString();


        return !getValidPeerID(multi).isEmpty();

    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {


        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.peer_view);


        String pid = null;
        String address = null;
        Bundle bundle = getArguments();
        if (bundle != null) {
            pid = bundle.getString(Content.PID);
            if (pid != null) {
                issueMessage.set(true);
            }
            address = bundle.getString(Content.ADDR);
        }

        mEditAccountLayout = dialog.findViewById(R.id.edit_account_layout);

        TextInputLayout mEditNameLayout = dialog.findViewById(R.id.edit_name_layout);
        Objects.requireNonNull(mEditNameLayout);
        mEditNameLayout.setCounterEnabled(true);
        mEditNameLayout.setCounterMaxLength(30);

        mName = dialog.findViewById(R.id.edit_name);
        InputFilter[] filterTitle = new InputFilter[1];
        filterTitle[0] = new InputFilter.LengthFilter(30);
        mName.setFilters(filterTitle);

        mMultihash = dialog.findViewById(R.id.multihash);
        Objects.requireNonNull(mMultihash);
        mAddress = dialog.findViewById(R.id.address);
        Objects.requireNonNull(mAddress);
        if (pid != null) {
            mMultihash.setText(pid);
        }
        if (address != null) {
            mAddress.setText(address);
        }

        mMultihash.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                mEditAccountLayout.setError(null);
            }
        });

        TextView mScanAccount = dialog.findViewById(R.id.scan_account);
        Objects.requireNonNull(mScanAccount);
        if (!hasCamera) {
            mScanAccount.setVisibility(View.GONE);
        } else {
            mScanAccount.setOnClickListener((v) -> clickInvokeScan());
        }

        TextView mOk = dialog.findViewById(R.id.ok);
        Objects.requireNonNull(mOk);
        mOk.setOnClickListener((v) -> {


            if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                return;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            removeKeyboards();


            if (!isValidPeerID()) {
                mEditAccountLayout.setError(getString(R.string.pid_not_valid));
                return;
            }


            Editable text = mMultihash.getText();
            Objects.requireNonNull(text);
            String hash = getValidPeerID(text.toString());


            String name = null;
            Editable textName = mName.getText();
            if (textName != null) {
                name = textName.toString();
            }

            String multiAddress = getAddress();


            clickConnectPeer(hash, name, multiAddress);


        });

        return dialog;
    }

    @Nullable
    private String getAddress() {

        try {
            Editable textAddress = mAddress.getText();
            if (textAddress != null) {
                String multiAddress = textAddress.toString();


                if (multiAddress.startsWith("/ip4/") || multiAddress.startsWith("/ip6/")) {

                    if (multiAddress.contains(Content.CIRCUIT)) {
                        return null;
                    }

                    String style = "/p2p/";
                    if (multiAddress.contains(style)) {
                        int index = multiAddress.indexOf(style);
                        return multiAddress.substring(0, index);
                    }


                    if (multiAddress.endsWith("/")) {
                        multiAddress = multiAddress.substring(0, multiAddress.length() - 1);
                    }
                    return multiAddress;
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    private void invokeScan() {
        try {
            PackageManager pm = mContext.getPackageManager();

            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
                integrator.setOrientationLocked(false);
                Intent intent = integrator.createScanIntent();
                mScanRequestForResult.launch(intent);
            } else {
                EVENTS.getInstance(mContext).permission(
                        getString(R.string.feature_camera_required));
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    private void clickInvokeScan() {

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        invokeScan();
    }

    private void removeKeyboards() {

        try {
            InputMethodManager imm = (InputMethodManager)
                    mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mMultihash.getWindowToken(), 0);
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


    private void clickConnectPeer(@NonNull String pid, @Nullable String name, @Nullable String address) {
        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            if (ipfs.decodeName(pid).isEmpty()) {
                EVENTS.getInstance(mContext).error(getString(R.string.pid_not_valid));
                return;
            }

            if (pid.equals(ipfs.getPeerID())) {
                EVENTS.getInstance(mContext).warning(getString(R.string.same_pid_like_host));
                return;
            }


            LiteService.connect(mContext, pid, name, address, issueMessage.get());
        } finally {
            dismiss();
        }
    }

}

