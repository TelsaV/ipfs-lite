package threads.server.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Objects;

import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.events.EventViewModel;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.services.DaemonService;
import threads.server.services.LiteService;
import threads.server.utils.MimeType;
import threads.server.work.PageWorker;

public class SettingsFragment extends Fragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private Context mContext;
    private TextView mSwarmKey;
    private final ActivityResultLauncher<Intent> mFileForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();

                        try {
                            Objects.requireNonNull(data);

                            Uri uri = data.getData();
                            Objects.requireNonNull(uri);
                            if (!FileDocumentsProvider.hasReadPermission(mContext, uri)) {
                                EVENTS.getInstance(mContext).error(
                                        getString(R.string.file_has_no_read_permission));
                                return;
                            }

                            if (FileDocumentsProvider.getFileSize(mContext, uri) > 500) {
                                EVENTS.getInstance(mContext).error(
                                        getString(R.string.swarm_key_not_valid));
                            }

                            try (InputStream is = mContext.getContentResolver().openInputStream(uri)) {
                                Objects.requireNonNull(is);
                                String key;
                                try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                                    IPFS.copy(is, os);
                                    key = os.toString();
                                }

                                IPFS ipfs = IPFS.getInstance(mContext);
                                ipfs.checkSwarmKey(key);

                                IPFS.setSwarmKey(mContext, key);
                                mSwarmKey.setText(key);

                                if (IPFS.isPrivateNetworkEnabled(mContext)) {
                                    EVENTS.getInstance(mContext).exit(
                                            getString(R.string.daemon_restart_config_changed));
                                }
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                                EVENTS.getInstance(mContext).error(
                                        getString(R.string.swarm_key_not_valid));
                            }


                        } catch (Throwable e) {
                            LogUtils.error(TAG, e);
                        }
                    }
                }
            });

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    private String getData(@NonNull Context context, long size) {

        String fileSize;

        if (size < 1000) {
            fileSize = String.valueOf(size);
            return context.getString(R.string.traffic, fileSize);
        } else if (size < 1000 * 1000) {
            fileSize = String.valueOf((double) (size / 1000));
            return context.getString(R.string.traffic_kb, fileSize);
        } else {
            fileSize = String.valueOf((double) (size / (1000 * 1000)));
            return context.getString(R.string.traffic_mb, fileSize);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        IPFS ipfs = IPFS.getInstance(mContext);


        TextView seeding = view.findViewById(R.id.seeding);
        TextView leeching = view.findViewById(R.id.leeching);
        TextView reachable = view.findViewById(R.id.reachable);
        TextView port = view.findViewById(R.id.port);

        port.setText(String.valueOf(ipfs.getSwarmPort()));

        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);

        eventViewModel.getSeeding().observe(getViewLifecycleOwner(), (event) -> {
            try {
                seeding.setText(getData(mContext, ipfs.getSeeding()));
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });

        eventViewModel.getLeeching().observe(getViewLifecycleOwner(), (event) -> {
            try {
                leeching.setText(getData(mContext, ipfs.getLeeching()));
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });

        eventViewModel.getReachable().observe(getViewLifecycleOwner(), (event) -> {
            try {
                reachable.setText(ipfs.getReachable().name());
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });
        boolean issueWarning = ipfs.isPrivateNetwork() || IPFS.isPrivateSharingEnabled(mContext);


        TextView warning_text = view.findViewById(R.id.warning_text);
        if (!issueWarning) {
            warning_text.setVisibility(View.GONE);
        } else {
            warning_text.setVisibility(View.VISIBLE);
            if (IPFS.isPrivateSharingEnabled(mContext)) {
                warning_text.setText(getString(R.string.private_sharing));
            } else {
                warning_text.setText(getString(R.string.private_network));
            }
        }


        ImageView daemonStart = view.findViewById(R.id.daemon_start);
        daemonStart.setOnClickListener(view1 -> {
            EVENTS.getInstance(mContext).warning(getString(R.string.server_mode));
            DaemonService.start(mContext);
        });


        SwitchMaterial enableRedirectUrl = view.findViewById(R.id.enable_redirect_url);
        Objects.requireNonNull(enableRedirectUrl);
        enableRedirectUrl.setChecked(InitApplication.isRedirectUrlEnabled(mContext));
        enableRedirectUrl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    InitApplication.setRedirectUrlEnabled(mContext, isChecked);

                    EVENTS.getInstance(mContext).exit(
                            getString(R.string.restart_config_changed));
                }
        );

        SwitchMaterial enableRedirectIndex = view.findViewById(R.id.enable_redirect_index);
        Objects.requireNonNull(enableRedirectIndex);
        enableRedirectIndex.setChecked(InitApplication.isRedirectIndexEnabled(mContext));
        enableRedirectIndex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    InitApplication.setRedirectIndexEnabled(mContext, isChecked);

                    EVENTS.getInstance(mContext).exit(
                            getString(R.string.restart_config_changed));
                }
        );


        TextView automatic_discovery_mode_text = view.findViewById(R.id.automatic_discovery_mode_text);

        String auto_discovery_html = getString(R.string.automatic_discovery_mode_text);
        automatic_discovery_mode_text.setTextAppearance(android.R.style.TextAppearance_Small);
        automatic_discovery_mode_text.setText(Html.fromHtml(auto_discovery_html, Html.FROM_HTML_MODE_LEGACY));

        SwitchMaterial automatic_discovery_mode = view.findViewById(R.id.automatic_discovery_mode);
        automatic_discovery_mode.setChecked(InitApplication.isAutoDiscovery(mContext));
        automatic_discovery_mode.setOnCheckedChangeListener((buttonView, isChecked) ->
                InitApplication.setAutoDiscovery(mContext, isChecked)
        );


        TextView private_sharing_mode_text = view.findViewById(R.id.private_sharing_mode_text);

        String private_sharing_mode_html = getString(R.string.private_sharing_mode_text);
        private_sharing_mode_text.setTextAppearance(android.R.style.TextAppearance_Small);
        private_sharing_mode_text.setText(Html.fromHtml(private_sharing_mode_html, Html.FROM_HTML_MODE_LEGACY));


        SwitchMaterial private_sharing_mode = view.findViewById(R.id.private_sharing_mode);
        private_sharing_mode.setChecked(IPFS.isPrivateSharingEnabled(mContext));
        private_sharing_mode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    try {
                        IPFS.setPrivateSharingEnabled(mContext, isChecked);
                        EVENTS.getInstance(mContext).exit(
                                getString(R.string.daemon_restart_config_changed));
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
        );


        TextView publisher_service_time_text = view.findViewById(R.id.publisher_service_time_text);
        SeekBar publisher_service_time = view.findViewById(R.id.publisher_service_time);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            publisher_service_time.setMin(2);
        }
        publisher_service_time.setMax(12);
        int time = 0;
        int pinServiceTime = LiteService.getPublishServiceTime(mContext);
        if (pinServiceTime > 0) {
            time = (pinServiceTime);
        }
        publisher_service_time_text.setText(getString(R.string.publisher_service_time,
                String.valueOf(time)));
        publisher_service_time.setProgress(time);
        publisher_service_time.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                LiteService.setPublisherServiceTime(mContext, progress);
                PageWorker.publish(mContext);
                publisher_service_time_text.setText(
                        getString(R.string.publisher_service_time,
                                String.valueOf(progress)));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }
        });

        TextView connection_timeout_text = view.findViewById(R.id.connection_timeout_text);
        SeekBar connection_timeout = view.findViewById(R.id.connection_timeout);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connection_timeout.setMin(15);
        }
        connection_timeout.setMax(120);

        int connectionTimeout = InitApplication.getConnectionTimeout(mContext);

        connection_timeout_text.setText(getString(R.string.connection_timeout,
                String.valueOf(connectionTimeout)));
        connection_timeout.setProgress(connectionTimeout);
        connection_timeout.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                InitApplication.setConnectionTimeout(mContext, progress);
                connection_timeout_text.setText(
                        getString(R.string.connection_timeout,
                                String.valueOf(progress)));

            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }
        });


        mSwarmKey = view.findViewById(R.id.swarm_key);
        mSwarmKey.setText(IPFS.getSwarmKey(mContext));


        ImageView swarm_key_action = view.findViewById(R.id.swarm_key_action);
        swarm_key_action.setOnClickListener(v -> {

            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType(MimeType.ALL);
                String[] mimeTypes = {MimeType.ALL};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, false);
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                mFileForResult.launch(intent);

            } catch (Throwable e) {
                EVENTS.getInstance(mContext).warning(
                        getString(R.string.no_activity_found_to_handle_uri));
            }
        });


        SwitchMaterial enable_private_network = view.findViewById(R.id.enable_private_network);
        enable_private_network.setChecked(IPFS.isPrivateNetworkEnabled(mContext));
        enable_private_network.setOnCheckedChangeListener((buttonView, isChecked) -> {
            IPFS.setPrivateNetworkEnabled(mContext, isChecked);
            EVENTS.getInstance(mContext).exit(
                    getString(R.string.daemon_restart_config_changed));

        });


    }

}
