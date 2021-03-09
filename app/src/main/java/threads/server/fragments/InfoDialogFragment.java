package threads.server.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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

import io.ipfs.LogUtils;
import threads.server.R;
import threads.server.core.Content;

public class InfoDialogFragment extends DialogFragment {

    public static final String TAG = InfoDialogFragment.class.getSimpleName();

    private Context mContext;


    public static InfoDialogFragment newInstance(@NonNull Uri uri, @NonNull String name,
                                                 @NonNull String message, @NonNull String address) {

        Bundle bundle = new Bundle();
        bundle.putString(Content.URI, uri.toString());
        bundle.putString(Content.NAME, name);
        bundle.putString(Content.TEXT, message);
        bundle.putString(Content.ADDRESS, address);
        InfoDialogFragment fragment = new InfoDialogFragment();
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
        View view = inflater.inflate(R.layout.dialog_info, null);

        ImageView imageView = view.findViewById(R.id.dialog_server_info);
        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        String title = getString(R.string.peer_id);
        String message = bundle.getString(Content.TEXT);
        Uri uri = Uri.parse(bundle.getString(Content.URI));
        String address = bundle.getString(Content.ADDRESS);
        Objects.requireNonNull(address);
        String name = bundle.getString(Content.NAME);
        Objects.requireNonNull(name);

        TextView multiAddress = view.findViewById(R.id.multi_address);


        if (address.isEmpty()) {
            multiAddress.setVisibility(View.GONE);
        } else {
            multiAddress.setText(address);
            if (address.contains("p2p-circuit")) {
                multiAddress.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.transit_connection, 0, 0, 0);
            }
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
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dismiss())
                .create();

        Dialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
        }

        return dialog;
    }


}
