package threads.server.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;

import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.utils.MimeType;

public class AccountDialogFragment extends DialogFragment {
    public static final String TAG = AccountDialogFragment.class.getSimpleName();

    private Context mContext;


    public static AccountDialogFragment newInstance(@NonNull Uri uri) {

        Bundle bundle = new Bundle();
        bundle.putString(Content.URI, uri.toString());
        AccountDialogFragment fragment = new AccountDialogFragment();
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

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        Uri uri = Uri.parse(bundle.getString(Content.URI));
        Objects.requireNonNull(uri);

        DOCS docs = DOCS.getInstance(mContext);
        Uri url = docs.getPinsPageUri();

        LayoutInflater inflater = LayoutInflater.from(mContext);

        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.account_dialog, null);


        ImageView imageView = view.findViewById(R.id.image_pid);


        TextView homepage = view.findViewById(R.id.homepage);
        homepage.setText(url.toString());


        try {
            Glide.with(mContext).
                    load(uri).
                    into(imageView);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.account_address)
                .setMessage(R.string.user_account_address_message)
                .setView(view)
                .setNeutralButton(android.R.string.cancel, (dialogInterface, i) -> dismiss())
                .setPositiveButton(R.string.share, (dialogInterface, i) ->
                        shareQRCode(uri, url));

        Dialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
        }

        return dialog;
    }


    private void shareQRCode(@NonNull Uri uri, @NonNull Uri url) {

        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            String peerID = ipfs.getPeerID().toBase58();
            Objects.requireNonNull(peerID);
            String text = getString(R.string.account_access);

            String peerId = getString(R.string.peer_id).concat("\n").concat(peerID);
            text = text.concat("\n\n").concat(peerId);
            String homepage = getString(R.string.homepage).concat("\n").concat(url.toString());
            text = text.concat("\n\n").concat(homepage);


            ComponentName[] names = {new ComponentName(mContext, MainActivity.class)};

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_SUBJECT, peerID);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setType(MimeType.RFC_822);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


            Intent chooser = Intent.createChooser(intent, getText(R.string.share));
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
            chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);


        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        } finally {
            dismiss();
        }
    }

}
