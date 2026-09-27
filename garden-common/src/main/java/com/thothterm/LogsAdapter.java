/*
 * Copyright (C) 2026 ThothTerm.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thothterm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.thothterm.logging.LogEntry;
import com.thothterm.logging.LogLevel;

import java.util.ArrayList;
import java.util.List;

public class LogsAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final int[] levelColors;
    private final List<LogEntry> entries = new ArrayList<>();

    public LogsAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
        this.levelColors = new int[]{
                ContextCompat.getColor(context, R.color.log_level_error),
                ContextCompat.getColor(context, R.color.log_level_warn),
                ContextCompat.getColor(context, R.color.log_level_info),
                ContextCompat.getColor(context, R.color.log_level_debug),
                ContextCompat.getColor(context, R.color.log_level_verbose)
        };
    }

    public void setEntries(List<LogEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public LogEntry getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_log, null);
            holder = new ViewHolder();
            holder.time = convertView.findViewById(R.id.log_time);
            holder.level = convertView.findViewById(R.id.log_level);
            holder.category = convertView.findViewById(R.id.log_category);
            holder.message = convertView.findViewById(R.id.log_message);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        LogEntry entry = entries.get(position);
        holder.time.setText(entry.timeText());
        holder.level.setText(entry.level.label);
        holder.level.setTextColor(levelColors[entry.level.rank]);
        holder.category.setText(entry.category);
        holder.message.setText(entry.message);
        return convertView;
    }

    private static class ViewHolder {
        TextView time;
        TextView level;
        TextView category;
        TextView message;
    }
}
