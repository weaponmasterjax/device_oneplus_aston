package org.lineageos.settings.memc;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.appbar.AppBarLayout;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemcSettingsFragment extends PreferenceFragmentCompat
        implements ApplicationsState.Callbacks {

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private Map<String, ApplicationsState.AppEntry> mEntryMap =
            new HashMap<String, ApplicationsState.AppEntry>();

    private RecyclerView mAppsRecyclerView;

    private MemcUtils mMemcUtils;
    private String mSearchFilter;
    private OnBackPressedCallback mSearchOnBackPressedCallback;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mSession.onResume();
        mActivityFilter = new ActivityFilter(getActivity().getPackageManager());

        mAllPackagesAdapter = new AllPackagesAdapter(getActivity());

        mMemcUtils = new MemcUtils(getActivity());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.search);
        if (searchItem != null) {
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    if (mSearchOnBackPressedCallback == null) {
                        mSearchOnBackPressedCallback = new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (item != null && item.isActionViewExpanded()) {
                                    item.collapseActionView();
                                }
                            }
                        };
                        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), mSearchOnBackPressedCallback);
                    } else {
                        mSearchOnBackPressedCallback.setEnabled(true);
                    }

                    if (getActivity() instanceof CollapsingToolbarBaseActivity) {
                        CollapsingToolbarBaseActivity activity = (CollapsingToolbarBaseActivity) getActivity();
                        AppBarLayout appBarLayout = activity.getAppBarLayout();
                        if (appBarLayout == null) {
                            appBarLayout = activity.findViewById(com.android.settingslib.collapsingtoolbar.R.id.app_bar);
                        }
                        if (appBarLayout != null) {
                            appBarLayout.setExpanded(false, true);
                        }
                    }
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (mSearchOnBackPressedCallback != null) {
                        mSearchOnBackPressedCallback.setEnabled(false);
                    }

                    if (getActivity() instanceof CollapsingToolbarBaseActivity) {
                        CollapsingToolbarBaseActivity activity = (CollapsingToolbarBaseActivity) getActivity();
                        AppBarLayout appBarLayout = activity.getAppBarLayout();
                        if (appBarLayout == null) {
                            appBarLayout = activity.findViewById(com.android.settingslib.collapsingtoolbar.R.id.app_bar);
                        }
                        if (appBarLayout != null) {
                            appBarLayout.setExpanded(true, true);
                        }
                    }
                    return true;
                }
            });
            View actionView = searchItem.getActionView();
            if (actionView instanceof android.widget.SearchView) {
                android.widget.SearchView searchView = (android.widget.SearchView) actionView;
                searchView.setQueryHint(getString(android.R.string.search_go));
                searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        mSearchFilter = newText;
                        mAllPackagesAdapter.filter(newText);
                        return true;
                    }
                });
            } else if (actionView instanceof androidx.appcompat.widget.SearchView) {
                androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) actionView;
                searchView.setQueryHint(getString(android.R.string.search_go));
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        mSearchFilter = newText;
                        mAllPackagesAdapter.filter(newText);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.memc_layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAppsRecyclerView = view.findViewById(R.id.memc_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSession.onPause();
        mSession.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String> sections = new ArrayList<String>();
        final ArrayList<Integer> positions = new ArrayList<Integer>();
        final PackageManager pm = getActivity().getPackageManager();
        String lastSectionIndex = null;
        int offset = 0;

        for (int i = 0; i < entries.size(); i++) {
            final ApplicationInfo info = entries.get(i).info;
            final String label = (String) info.loadLabel(pm);
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--"; // XXX
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null ||
                    !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }

            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
        mEntryMap.clear();
        for (ApplicationsState.AppEntry e : entries) {
            mEntryMap.put(e.info.packageName, e);
        }
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private ImageView icon;
        private ImageView configIndicator;
        private View rootView;

        private ViewHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.app_name);
            this.icon = view.findViewById(R.id.app_icon);
            this.configIndicator = view.findViewById(R.id.app_config_indicator);
            this.rootView = view;

            view.setTag(this);
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder> {

        private List<ApplicationsState.AppEntry> mAllEntries = new ArrayList<>();
        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[] mPositions;

        public AllPackagesAdapter(Context context) {
            mActivityFilter = new ActivityFilter(context.getPackageManager());
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.memc_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            ApplicationsState.AppEntry entry = mEntries.get(position);

            if (entry == null) {
                return;
            }
            holder.title.setText(entry.label);
            holder.itemView.setOnClickListener(v -> showConfigDialog(entry, holder.getBindingAdapterPosition()));
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);

            String packageName = entry.info.packageName;
            boolean hasConfig = mMemcUtils.hasPackageConfig(packageName);
            if (hasConfig) {
                holder.configIndicator.setImageResource(R.drawable.ic_config_avail);
                holder.configIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.configIndicator.setImageResource(R.drawable.ic_config_empty);
                holder.configIndicator.setVisibility(View.VISIBLE);
            }
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mAllEntries = new ArrayList<>(entries);
            mSections = sections.toArray(new String[sections.size()]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                mPositions[i] = positions.get(i);
            }
            filter(mSearchFilter);
        }

        public void filter(String query) {
            if (mAllEntries == null || mAllEntries.isEmpty()) {
                return;
            }
            if (TextUtils.isEmpty(query)) {
                mEntries = new ArrayList<>(mAllEntries);
            } else {
                String lowerQuery = query.toLowerCase(Locale.getDefault());
                List<ApplicationsState.AppEntry> filtered = new ArrayList<>();
                for (ApplicationsState.AppEntry entry : mAllEntries) {
                    if (entry == null) continue;
                    boolean matchesLabel = entry.label != null && entry.label.toLowerCase(Locale.getDefault()).contains(lowerQuery);
                    boolean matchesPkg = entry.info != null && entry.info.packageName != null && entry.info.packageName.toLowerCase(Locale.getDefault()).contains(lowerQuery);
                    if (matchesLabel || matchesPkg) {
                        filtered.add(entry);
                    }
                }
                mEntries = filtered;
            }
            notifyDataSetChanged();
        }

        private void showConfigDialog(ApplicationsState.AppEntry entry, int position) {
            Context context = getActivity();
            String pkg = entry.info.packageName;
            String existing = mMemcUtils.getConfigForPackage(pkg);

            final EditText input = new EditText(context);
            input.setMinLines(4);
            input.setText(existing != null ? existing : "");

            new AlertDialog.Builder(context)
                    .setTitle(R.string.memc_config_dialog_title)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        String value = input.getText().toString();
                        mMemcUtils.writePackageConfig(pkg, value);
                        if (position != RecyclerView.NO_POSITION) {
                            notifyItemChanged(position);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<String>();

        private ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;

            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : resolveInfoList) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            boolean show = !mAllPackagesAdapter.mEntries.contains(entry.info.packageName);
            if (show) {
                synchronized (mLauncherResolveInfoList) {
                    show = mLauncherResolveInfoList.contains(entry.info.packageName);
                }
            }
            return show;
        }
    }
}
