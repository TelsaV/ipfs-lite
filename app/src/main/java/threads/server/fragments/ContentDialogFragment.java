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

import threads.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.Content;
import threads.server.utils.MimeType;

public class ContentDialogFragment extends DialogFragment {

    public static final String TAG = ContentDialogFragment.class.getSimpleName();

    private Context mContext;


    public static ContentDialogFragment newInstance(
            @NonNull Uri uri, @NonNull String name, @NonNull String message, @NonNull String url) {


        Bundle bundle = new Bundle();
        bundle.putString(Content.URI, uri.toString());
        bundle.putString(Content.NAME, name);
        bundle.putString(Content.TEXT, message);
        bundle.putString(Content.URL, url);
        ContentDialogFragment fragment = new ContentDialogFragment();
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

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.content_info, null);

        ImageView imageView = view.findViewById(R.id.dialog_server_info);
        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        String title = getString(R.string.content_id);
        String message = bundle.getString(Content.TEXT, "");
        Uri uri = Uri.parse(bundle.getString(Content.URI));
        Objects.requireNonNull(uri);
        String url = bundle.getString(Content.URL, "");
        String name = bundle.getString(Content.NAME);
        Objects.requireNonNull(name);


        TextView page = view.findViewById(R.id.page);

        if (url.isEmpty()) {
            page.setVisibility(View.GONE);
        } else {
            page.setText(url);
        }


        try {

            Glide.with(mContext).
                    load(uri).
                    into(imageView);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        builder.setTitle(title)
                .setMessage(message)
                .setView(view)
                .setNeutralButton(android.R.string.cancel, (dialogInterface, i) -> dismiss())
                .setPositiveButton(R.string.share, (dialogInterface, i) ->
                        shareQRCode(uri, name, message, url))
                .create();

        Dialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
        }

        return dialog;
    }


    private void shareQRCode(@NonNull Uri uri, @NonNull String name, @NonNull String message, @NonNull String url) {

        try {
            String text = message;
            if (!url.isEmpty()) {
                text = text.concat("\n\n").concat(url);
            }

            ComponentName[] names = {new ComponentName(mContext, MainActivity.class)};

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_SUBJECT, name);
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


        } catch (Throwable ignore) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        } finally {
            dismiss();
        }
    }

}
