package threads.server.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.events.EventViewModel;
import threads.server.services.DaemonService;
import threads.server.services.LiteService;
import threads.server.work.PageWorker;

public class SettingsFragment extends Fragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private Context mContext;

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

        port.setText(String.valueOf(ipfs.getPort()));

        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);

        eventViewModel.getSeeding().observe(getViewLifecycleOwner(), (event) -> {
            try {
                seeding.setText(getData(mContext, 0));// TODO
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });

        eventViewModel.getLeeching().observe(getViewLifecycleOwner(), (event) -> {
            try {
                leeching.setText(getData(mContext, 0));// TODO
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

        TextView warning_text = view.findViewById(R.id.warning_text);

        warning_text.setVisibility(View.GONE);


        ImageView daemonStart = view.findViewById(R.id.daemon_start);
        daemonStart.setOnClickListener(view1 -> {
            EVENTS.getInstance(mContext).warning(getString(R.string.server_mode));
            DaemonService.start(mContext);
        });


        SwitchMaterial enableRedirectUrl = view.findViewById(R.id.enable_redirect_url);
        Objects.requireNonNull(enableRedirectUrl);
        enableRedirectUrl.setChecked(Settings.isRedirectUrlEnabled(mContext));
        enableRedirectUrl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectUrlEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );

        SwitchMaterial enableRedirectIndex = view.findViewById(R.id.enable_redirect_index);
        Objects.requireNonNull(enableRedirectIndex);
        enableRedirectIndex.setChecked(Settings.isRedirectIndexEnabled(mContext));
        enableRedirectIndex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectIndexEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );


        SwitchMaterial enableJavascript = view.findViewById(R.id.enable_javascript);
        Objects.requireNonNull(enableJavascript);
        enableJavascript.setChecked(Settings.isJavascriptEnabled(mContext));
        enableJavascript.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setJavascriptEnabled(mContext, isChecked);

                    EVENTS.getInstance(mContext).javascript();
                }
        );

        TextView automatic_discovery_mode_text = view.findViewById(R.id.automatic_discovery_mode_text);

        String auto_discovery_html = getString(R.string.automatic_discovery_mode_text);
        automatic_discovery_mode_text.setTextAppearance(android.R.style.TextAppearance_Small);
        automatic_discovery_mode_text.setText(Html.fromHtml(auto_discovery_html, Html.FROM_HTML_MODE_LEGACY));

        SwitchMaterial automatic_discovery_mode = view.findViewById(R.id.automatic_discovery_mode);
        automatic_discovery_mode.setChecked(Settings.isAutoDiscovery(mContext));
        automatic_discovery_mode.setOnCheckedChangeListener((buttonView, isChecked) ->
                Settings.setAutoDiscovery(mContext, isChecked)
        );


        TextView publisher_service_time_text = view.findViewById(R.id.publisher_service_time_text);
        SeekBar publisher_service_time = view.findViewById(R.id.publisher_service_time);

        publisher_service_time.setMin(2);
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

        boolean publisherEnabled = Settings.isPublisherEnabled(mContext);
        SwitchMaterial enablePublisher = view.findViewById(R.id.enable_publisher);
        Objects.requireNonNull(enablePublisher);
        enablePublisher.setChecked(publisherEnabled);
        enablePublisher.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setPublisherEnabled(mContext, isChecked);
                    publisher_service_time.setEnabled(isChecked);
                    publisher_service_time_text.setEnabled(isChecked);
                    EVENTS.getInstance(mContext).home();
                }
        );

        if (publisherEnabled) {
            publisher_service_time.setEnabled(true);
            publisher_service_time_text.setEnabled(true);
        } else {
            publisher_service_time.setEnabled(false);
            publisher_service_time_text.setEnabled(false);
        }


        TextView connection_timeout_text = view.findViewById(R.id.connection_timeout_text);
        SeekBar connection_timeout = view.findViewById(R.id.connection_timeout);


        connection_timeout.setMin(15);
        connection_timeout.setMax(120);

        int connectionTimeout = Settings.getConnectionTimeout(mContext);

        connection_timeout_text.setText(getString(R.string.connection_timeout,
                String.valueOf(connectionTimeout)));
        connection_timeout.setProgress(connectionTimeout);
        connection_timeout.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                Settings.setConnectionTimeout(mContext, progress);
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


    }

}
