/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package info.lamatricexiste.network.activities;

import info.lamatricexiste.network.R;
import info.lamatricexiste.network.db.Save;
import info.lamatricexiste.network.network.HostBean;
import info.lamatricexiste.network.network.NetInfo;
import info.lamatricexiste.network.tasks.AbstractDiscovery;
import info.lamatricexiste.network.tasks.DefaultDiscovery;
import info.lamatricexiste.network.tasks.DnsDiscovery;
import info.lamatricexiste.network.utils.Export;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

final public class DiscoveryActivity extends BaseNetActivity implements OnItemClickListener {

    private final String TAG = "ActivityDiscovery";
    public final static long VIBRATE = (long) 250;
    public final static int SCAN_PORT_RESULT = 1;
    public static final int MENU_SCAN_SINGLE = 0;
    public static final int MENU_OPTIONS = 1;
    public static final int MENU_HELP = 2;
    private static final int MENU_EXPORT = 3;
    private static LayoutInflater mInflater;
    private int currentNetwork = 0;
    private long network_ip = 0;
    private long network_start = 0;
    private long network_end = 0;
    private List<HostBean> hosts = null;
    private HostsAdapter adapter;
    private Button btn_discover;
    private AbstractDiscovery mDiscoveryTask = null;

    // private SlidingDrawer mDrawer;

    public static void scanSingle(final Context ctxt, String ip) {
        // Alert dialog
        View v = LayoutInflater.from(ctxt).inflate(R.layout.scan_single, null);
        final EditText txt = (EditText) v.findViewById(R.id.ip);
        if (ip != null) {
            txt.setText(ip);
        }
        AlertDialog.Builder dialogIp = new AlertDialog.Builder(ctxt);
        dialogIp.setTitle(R.string.scan_single_title);
        dialogIp.setView(v);
        dialogIp.setPositiveButton(R.string.btn_scan, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dlg, int sumthin) {
                // start scanportactivity
                Intent intent = new Intent(ctxt, PortscanActivity.class);
                intent.putExtra(HostBean.EXTRA_HOST, txt.getText().toString());
                try {
                    intent.putExtra(HostBean.EXTRA_HOSTNAME, (InetAddress.getByName(txt.getText()
                            .toString()).getHostName()));
                } catch (UnknownHostException e) {
                    intent.putExtra(HostBean.EXTRA_HOSTNAME, txt.getText().toString());
                }
                ctxt.startActivity(intent);
            }
        });
        dialogIp.setNegativeButton(R.string.btn_discover_cancel, null);
        dialogIp.show();
    }

    public void addHost(HostBean host) {
        host.position = hosts.size();
        hosts.add(host);
        adapter.add(null);
    }

    protected void cancelTasks() {
        if (mDiscoveryTask != null) {
            mDiscoveryTask.cancel(true);
            mDiscoveryTask = null;
        }
    }

    private void export() {
        final Export e = new Export(ctxt, hosts);
        final String file = e.getFileName();

        View v = mInflater.inflate(R.layout.dialog_edittext, null);
        final EditText txt = (EditText) v.findViewById(R.id.edittext);
        txt.setText(file);

        AlertDialog.Builder getFileName = new AlertDialog.Builder(this);
        getFileName.setTitle(R.string.export_choose);
        getFileName.setView(v);
        getFileName.setPositiveButton(R.string.export_save, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dlg, int sumthin) {
                final String fileEdit = txt.getText().toString();
                if (e.fileExists(fileEdit)) {
                    AlertDialog.Builder fileExists = new AlertDialog.Builder(DiscoveryActivity.this);
                    fileExists.setTitle(R.string.export_exists_title);
                    fileExists.setMessage(R.string.export_exists_msg);
                    fileExists.setPositiveButton(R.string.btn_yes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (e.writeToSd(fileEdit)) {
                                        makeToast(R.string.export_finished);
                                    } else {
                                        export();
                                    }
                                }
                            });
                    fileExists.setNegativeButton(R.string.btn_no, null);
                    fileExists.show();
                } else {
                    if (e.writeToSd(fileEdit)) {
                        makeToast(R.string.export_finished);
                    } else {
                        export();
                    }
                }
            }
        });
        getFileName.setNegativeButton(R.string.btn_discover_cancel, null);
        getFileName.show();
    }

    private void initList() {
        // setSelectedHosts(false);
        adapter.clear();
        hosts = new ArrayList<HostBean>();
    }

    public void makeToast(int msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // Listen for Activity results
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SCAN_PORT_RESULT:
                if (resultCode == RESULT_OK) {
                    // Get scanned ports
                    if (data != null && data.hasExtra(HostBean.EXTRA)) {
                        HostBean host = data.getParcelableExtra(HostBean.EXTRA);
                        if (host != null) {
                            hosts.set(host.position, host);
                        }
                    }
                }
            default:
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.discovery);
        mInflater = LayoutInflater.from(ctxt);

        // Discover
        btn_discover = (Button) findViewById(R.id.btn_discover);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDiscovering();
            }
        });

        // Options
        Button btn_options = (Button) findViewById(R.id.btn_options);
        btn_options.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ctxt, PreferencesActivity.class));
            }
        });

        // Hosts list
        adapter = new HostsAdapter(ctxt);
        ListView list = (ListView) findViewById(R.id.output);
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        list.setOnItemClickListener(this);
        list.setEmptyView(findViewById(R.id.list_empty));

        // Drawer
        /*
         * final View info = findViewById(R.id.info_container); mDrawer =
         * (SlidingDrawer) findViewById(R.id.drawer);
         * mDrawer.setOnDrawerScrollListener(new
         * SlidingDrawer.OnDrawerScrollListener() { public void
         * onScrollStarted() {
         * info.setBackgroundResource(R.drawable.drawer_bg2); }
         * 
         * public void onScrollEnded() { } });
         * mDrawer.setOnDrawerCloseListener(new
         * SlidingDrawer.OnDrawerCloseListener() { public void onDrawerClosed()
         * { info.setBackgroundResource(R.drawable.drawer_bg); } }); EditText
         * cidr_value = (EditText) findViewById(R.id.cidr_value); ((Button)
         * findViewById(R.id.btn_cidr_plus)).setOnClickListener(new
         * View.OnClickListener() { public void onClick(View v) { } });
         * ((Button) findViewById(R.id.btn_cidr_minus)).setOnClickListener(new
         * View.OnClickListener() { public void onClick(View v) { } });
         */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, DiscoveryActivity.MENU_SCAN_SINGLE, 0, R.string.scan_single_title).setIcon(
                android.R.drawable.ic_menu_mylocation);
        menu.add(0, DiscoveryActivity.MENU_EXPORT, 0, R.string.preferences_export).setIcon(
                android.R.drawable.ic_menu_save);
        menu.add(0, DiscoveryActivity.MENU_OPTIONS, 0, R.string.btn_options).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, DiscoveryActivity.MENU_HELP, 0, R.string.preferences_help).setIcon(
                android.R.drawable.ic_menu_help);
        return true;
    }

    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        final HostBean host = hosts.get(position);
        AlertDialog.Builder dialog = new AlertDialog.Builder(DiscoveryActivity.this);
        dialog.setTitle(R.string.discover_action_title);
        dialog.setItems(new CharSequence[] { getString(R.string.discover_action_scan),
                getString(R.string.discover_action_rename) }, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        // Start portscan
                        Intent intent = new Intent(ctxt, PortscanActivity.class);
                        intent.putExtra(EXTRA_WIFI, NetInfo.isConnected(ctxt));
                        intent.putExtra(HostBean.EXTRA, host);
                        startActivityForResult(intent, SCAN_PORT_RESULT);
                        break;
                    case 1:
                        // Change name
                        // FIXME: TODO

                        final View v = mInflater.inflate(R.layout.dialog_edittext, null);
                        final EditText txt = (EditText) v.findViewById(R.id.edittext);
                        final Save s = new Save();
                        txt.setText(s.getCustomName(host));

                        final AlertDialog.Builder rename = new AlertDialog.Builder(
                                DiscoveryActivity.this);
                        rename.setView(v);
                        rename.setTitle(R.string.discover_action_rename);
                        rename.setPositiveButton(R.string.btn_ok, new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final String name = txt.getText().toString();
                                host.hostname = name;
                                s.setCustomName(name, host.getHardwareAddress());
                                adapter.notifyDataSetChanged();
                                Toast.makeText(DiscoveryActivity.this,
                                        R.string.discover_action_saved, Toast.LENGTH_SHORT).show();
                            }
                        });
                        rename.setNegativeButton(R.string.btn_remove, new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                host.hostname = null;
                                s.removeCustomName(host.getHardwareAddress());
                                adapter.notifyDataSetChanged();
                                Toast.makeText(DiscoveryActivity.this,
                                        R.string.discover_action_deleted, Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                        rename.show();
                        break;
                }
            }
        });
        dialog.setNegativeButton(R.string.btn_discover_cancel, null);
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DiscoveryActivity.MENU_SCAN_SINGLE:
                scanSingle(this, null);
                return true;
            case DiscoveryActivity.MENU_OPTIONS:
                startActivity(new Intent(ctxt, PreferencesActivity.class));
                return true;
            case DiscoveryActivity.MENU_HELP:
                startActivity(new Intent(ctxt, HelpActivity.class));
                return true;
            case DiscoveryActivity.MENU_EXPORT:
                export();
                return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setButton(Button btn, int res, boolean disable) {
        if (disable) {
            setButtonOff(btn, res);
        } else {
            setButtonOn(btn, res);
        }
    }

    private void setButtonOff(Button b, int drawable) {
        b.setClickable(false);
        b.setEnabled(false);
        b.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
    }

    private void setButtonOn(Button b, int drawable) {
        b.setClickable(true);
        b.setEnabled(true);
        b.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
    }

    protected void setButtons(boolean disable) {
        if (disable) {
            setButtonOff(btn_discover, R.drawable.disabled);
        } else {
            setButtonOn(btn_discover, R.drawable.ic_action_search);
        }
    }

    protected void setInfo() {
        // Info
        ((TextView) findViewById(R.id.info_ip)).setText(info_ip_str);
        ((TextView) findViewById(R.id.info_in)).setText(info_in_str);
        ((TextView) findViewById(R.id.info_mo)).setText(info_mo_str);

        // Scan button state
        if (mDiscoveryTask != null) {
            setButton(btn_discover, R.drawable.ic_navigation_cancel, false);
            btn_discover.setText(R.string.btn_discover_cancel);
            btn_discover.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cancelTasks();
                }
            });
        }

        if (currentNetwork != net.hashCode()) {
            Log.i(TAG, "Network info has changed");
            currentNetwork = net.hashCode();

            // Cancel running tasks
            cancelTasks();
        } else {
            return;
        }

        // Get ip information
        network_ip = NetInfo.getUnsignedLongFromIp(net.ip);
        if (prefs.getBoolean(PreferencesActivity.KEY_IP_CUSTOM, PreferencesActivity.DEFAULT_IP_CUSTOM)) {
            // Custom IP
            network_start = NetInfo.getUnsignedLongFromIp(prefs.getString(PreferencesActivity.KEY_IP_START,
                    PreferencesActivity.DEFAULT_IP_START));
            network_end = NetInfo.getUnsignedLongFromIp(prefs.getString(PreferencesActivity.KEY_IP_END,
                    PreferencesActivity.DEFAULT_IP_END));
        } else {
            // Custom CIDR
            if (prefs.getBoolean(PreferencesActivity.KEY_CIDR_CUSTOM, PreferencesActivity.DEFAULT_CIDR_CUSTOM)) {
                net.cidr = Integer.parseInt(prefs.getString(PreferencesActivity.KEY_CIDR, PreferencesActivity.DEFAULT_CIDR));
            }
            // Detected IP
            int shift = (32 - net.cidr);
            if (net.cidr < 31) {
                network_start = (network_ip >> shift << shift) + 1;
                network_end = (network_start | ((1 << shift) - 1)) - 1;
            } else {
                network_start = (network_ip >> shift << shift);
                network_end = (network_start | ((1 << shift) - 1));
            }
            // Reset ip start-end (is it really convenient ?)
            Editor edit = prefs.edit();
            edit.putString(PreferencesActivity.KEY_IP_START, NetInfo.getIpFromLongUnsigned(network_start));
            edit.putString(PreferencesActivity.KEY_IP_END, NetInfo.getIpFromLongUnsigned(network_end));
            edit.commit();
        }
    }

    // private List<String> getSelectedHosts(){
    // List<String> hosts_s = new ArrayList<String>();
    // int listCount = list.getChildCount();
    // for(int i=0; i<listCount; i++){
    // CheckBox cb = (CheckBox) list.getChildAt(i).findViewById(R.id.list);
    // if(cb.isChecked()){
    // hosts_s.add(hosts.get(i));
    // }
    // }
    // return hosts_s;
    // }
    //    
    // private void setSelectedHosts(Boolean all){
    // int listCount = list.getChildCount();
    // for(int i=0; i<listCount; i++){
    // CheckBox cb = (CheckBox) list.getChildAt(i).findViewById(R.id.list);
    // if(all){
    // cb.setChecked(true);
    // } else {
    // cb.setChecked(false);
    // }
    // }
    // }

    // private void makeToast(String msg) {
    // Toast.makeText(getApplicationContext(), (CharSequence) msg,
    // Toast.LENGTH_SHORT).show();
    // }

    /**
     * Discover hosts
     */
    private void startDiscovering() {
        int method = 0;
        try {
            method = Integer.parseInt(prefs.getString(PreferencesActivity.KEY_METHOD_DISCOVER,
                    PreferencesActivity.DEFAULT_METHOD_DISCOVER));
        } catch (NumberFormatException e) {
            Log.e(TAG, e.getMessage());
        }
        switch (method) {
            case 1:
                mDiscoveryTask = new DnsDiscovery(DiscoveryActivity.this);
                break;
            case 2:
                // Root
                break;
            case 0:
            default:
                mDiscoveryTask = new DefaultDiscovery(DiscoveryActivity.this, info_ip_raw_str, info_mac_str);
        }
        mDiscoveryTask.setNetwork(network_ip, network_start, network_end);
        mDiscoveryTask.execute();
        btn_discover.setText(R.string.btn_discover_cancel);
        setButton(btn_discover, R.drawable.ic_navigation_cancel, false);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cancelTasks();
            }
        });
        makeToast(R.string.discover_start);
        setProgressBarVisibility(true);
        setProgressBarIndeterminateVisibility(true);
        initList();
    }

    public void stopDiscovering() {
        Log.e(TAG, "stopDiscovering()");
        mDiscoveryTask = null;
        setButtonOn(btn_discover, R.drawable.ic_action_search);
        btn_discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDiscovering();
            }
        });
        setProgressBarVisibility(false);
        setProgressBarIndeterminateVisibility(false);
        btn_discover.setText(R.string.btn_discover);
    }

    // Custom ArrayAdapter
    private class HostsAdapter extends ArrayAdapter<Void> {
    	private Context mContext;
    	
        public HostsAdapter(Context ctxt) {
            super(ctxt, R.layout.list_host, R.id.list);
            mContext = ctxt;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_host, null);
                holder = new ViewHolder();
                holder.host = (TextView) convertView.findViewById(R.id.list);
                holder.mac = (TextView) convertView.findViewById(R.id.mac);
                holder.vendor = (TextView) convertView.findViewById(R.id.vendor);
                holder.logo = (ImageView) convertView.findViewById(R.id.logo);
                holder.userGivenName = (TextView) convertView.findViewById(R.id.userName);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            final HostBean host = hosts.get(position);
            
            if (host.deviceType == HostBean.TYPE_GATEWAY) {
                holder.logo.setImageResource(R.drawable.ic_network_device_internet);
            } else if (host.isAlive == 1 || !host.getHardwareAddress().equalsIgnoreCase(NetInfo.NOMAC)) {
                holder.logo.setImageResource(host.icon);
                holder.logo.setColorFilter(null);
            } else {
                holder.logo.setImageResource(host.icon);
                holder.logo.setColorFilter(Color.parseColor("#DD0000"), Mode.MULTIPLY);
            }
            
            if(host.isThisThisDevice){
            	holder.logo.setColorFilter(mContext.getResources().getColor(R.color.highlight_dark), Mode.MULTIPLY);
            }
            
            if (host.hostname != null && !host.hostname.equals(host.ipAddress)) {
                holder.host.setText(host.hostname + " (" + host.ipAddress + ")");
            } else {
                holder.host.setText(host.ipAddress);
            }
            
            if (!host.getHardwareAddress().equalsIgnoreCase(NetInfo.NOMAC)) {
                holder.mac.setText(host.getHardwareAddress());
                if(host.nicVendor != null){
                    holder.vendor.setText(host.nicVendor);
                } else {
                    holder.vendor.setText(R.string.info_unknown);
                }
                holder.mac.setVisibility(View.VISIBLE);
                holder.vendor.setVisibility(View.VISIBLE);
            } else {
                holder.mac.setVisibility(View.GONE);
                holder.vendor.setVisibility(View.GONE);
            }
            
            if(host.userGivenName != null){
            	holder.userGivenName.setText(host.userGivenName);
            	holder.userGivenName.setVisibility(View.VISIBLE);
            } else {
            	holder.userGivenName.setVisibility(View.GONE);
            }
            
            return convertView;
        }
    }

    static class ViewHolder {
        TextView host;
        TextView mac;
        TextView vendor;
        TextView userGivenName;
        ImageView logo;
    }
}
