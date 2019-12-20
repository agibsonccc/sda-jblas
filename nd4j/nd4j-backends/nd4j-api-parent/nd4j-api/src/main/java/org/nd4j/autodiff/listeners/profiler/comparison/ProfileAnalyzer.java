/* ******************************************************************************
 * Copyright (c) 2019 Konduit K.K.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/
package org.nd4j.autodiff.listeners.profiler.comparison;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.listeners.profiler.ProfilingListener;
import org.nd4j.autodiff.listeners.profiler.data.Phase;
import org.nd4j.autodiff.listeners.profiler.data.TraceEvent;
import org.nd4j.autodiff.listeners.profiler.data.TraceEvents;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.converters.DifferentialFunctionClassHolder;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.list.NDArrayList;
import org.nd4j.shade.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A profile analyzer, used for analyzing Chrome-format profiler dumps generated by both SameDiff's<br>
 * {@link ProfilingListener} and TensorFlow's profiler.<br>
 * Has methods for summarizing profiler statistics, as well as comparing two profiler dumps.<br>
 * <br>
 * Also supports analyzing/aggregating multiple JSON files in a directory, via the "...Directory(...)" methods.
 * <p>
 * See {@link ProfilingListener}<br>
 * See {@link TraceEvent}
 *
 * @author Alex Black
 */
@Slf4j
public class ProfileAnalyzer {

    /**
     * Chrome profiler supports 2 formats:<br>
     * SameDiff == JSON Array Format<br>
     * TensorFlow == JSON Object Format<br>
     */
    public enum ProfileFormat {SAMEDIFF, TENSORFLOW}

    /**
     * Only applicable for profile comparisons.<br>
     * PROFILE1_PC - sort by profile 1 percentage of total time<br>
     * PROFILE2_PC - sort by profile 2 percentage of total time<br>
     * RATIO - sort by highest ratio (mean op time profile 1 / mean op time profile 2)
     */
    public enum SortBy {PROFILE1_PC, PROFILE2_PC, RATIO}


    /**
     * Summarize and print to stdout the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Format of the profiler file
     */
    public static void summarizeProfile(File file, ProfileFormat profileFormat) {
        System.out.println(summarizeProfileStr(file, profileFormat));
    }

    /**
     * Summarize and return as a string the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Format of the profiler file
     */
    public static String summarizeProfileStr(File file, ProfileFormat profileFormat) {
        TraceEvent[] events = getTraceEvents(file, profileFormat);
        return summarizeTraceEvents(events);
    }

    /**
     * Aggregate, summarize and print to stdout all .json profile files in the specified directory (not recursive)
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static void summarizeProfileDirectory(File dir, ProfileFormat profileFormat) {
        System.out.println(summarizeProfileDirectoryStr(dir, profileFormat));
    }

    /**
     * Aggregate, summarize and return as a String all .json profile files in the specified directory (not recursive)
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static String summarizeProfileDirectoryStr(File dir, ProfileFormat profileFormat) {
        return summarizeTraceEvents(getTraceEventsDir(dir, profileFormat));
    }

    /**
     * Load, aggregate and return the TraceEvent object from all profiles in the specified directory
     *
     * @param dir           Directory containing the profiles
     * @param profileFormat Profile format
     */
    public static TraceEvent[] getTraceEventsDir(File dir, ProfileFormat profileFormat) {
        File[] files = dir.listFiles();
        Preconditions.checkState(files != null && files.length > 0, "No profiles found in directory: %s", dir);
        List<TraceEvent> l = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".json")) {
                log.info("Skipping non-JSON file in directory - {}", f.getAbsolutePath());
                continue;
            }
            TraceEvent[] e = getTraceEvents(f, profileFormat);
            Collections.addAll(l, e);
        }
        return l.toArray(new TraceEvent[0]);
    }

    /**
     * Load and return the TraceEvent object from the specified profile file
     *
     * @param file          Profile file
     * @param profileFormat Profile format
     */
    public static TraceEvent[] getTraceEvents(File file, ProfileFormat profileFormat) {
        ObjectMapper json = ProfilingListener.jsonMapper();

        String content;
        try {
            content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!content.matches(".*]\\s*")) {
            if (content.endsWith(",")) {
                //Has comma, missing ]
                content = content.substring(0, content.length() - 1) + "]";
            } else if (content.endsWith(",\n")) {
                //Has comma and newline, missing ]
                content = content.substring(0, content.length() - 2) + "]";
            } else {
                content = content + "]";
            }
        }

        TraceEvent[] events;
        if (profileFormat == ProfileFormat.SAMEDIFF) {
            try {
                events = json.readValue(content, TraceEvent[].class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            //TF format
            TraceEvents traceEvents;
            try {
                traceEvents = json.readValue(content, TraceEvents.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            events = traceEvents.getTraceEvents().toArray(new TraceEvent[0]);

            //Clean up TF format - sometimes things like "Softmax" are actually profiled as "_MklSoftmax"
            //And we'll align TF names to SameDiff names
            for (TraceEvent te : events) {
                if (TF_PROFILE_ALIASES.containsKey(te.getName())) {
                    te.setName(TF_PROFILE_ALIASES.get(te.getName()));
                }

                DifferentialFunction df = DifferentialFunctionClassHolder.getInstance().getOpWithTensorflowName(te.getName());
                if (df != null) {
                    te.setName(df.opName());
                }
            }
        }

        return events;
    }

    /**
     * Summarize the specified TraceEvents as a String
     *
     * @param events Events to summarize
     */
    public static String summarizeTraceEvents(TraceEvent[] events) {
        Pair<Long, Map<String, OpStats>> p = aggregateTraceEvents(events);
        final Map<String, OpStats> stats = p.getSecond();
        long allOpsUs = p.getFirst();

        //Summarize by op type:
        List<String> l = new ArrayList<>(stats.keySet());
        Collections.sort(l, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Long.compare(stats.get(o1).sumUs, stats.get(o2).sumUs);
            }
        });

        //Work out longest name and op name:
        int longestName = 30;
        int longestOpName = 30;
        for (String s : l) {
            longestName = Math.max(longestName, s.length() + 1);
            longestOpName = Math.max(longestOpName, stats.get(s).opName.length() + 1);
        }

        StringBuilder sb = new StringBuilder();
        String headerFormat = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-10s%-10s%-10s%-10s\n";
        sb.append(String.format(headerFormat, "Op Name", "Op", "Count", "Total uS", "%", "Min", "Max", "Std"));
        String format = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-10.2f%-10d%-10d%-10.2f\n";
        for (String s : l) {
            OpStats st = stats.get(s);
            double pc = (100.0 * st.sumUs) / allOpsUs;
            INDArray arr = st.timesUs.array();
            long min = arr.minNumber().longValue();
            long max = arr.maxNumber().longValue();
            double std = arr.stdNumber().doubleValue();
            sb.append(String.format(format, s, st.opName, st.count, st.getSumUs(), pc, min, max, std));
        }

        return sb.toString();
    }

    private static Pair<Long, Map<String, OpStats>> aggregateTraceEvents(TraceEvent[] events) {
        //Summarize by op (instance) name:
        final Map<String, OpStats> stats = new HashMap<>();
        for (TraceEvent e : events) {
            if (e.getPh() != Phase.X || e.getDur() == null) {
                continue;
            }

            OpStats s;
            String instanceName = (String) e.getArgs().get("name");
            if (stats.containsKey(instanceName)) {
                s = stats.get(instanceName);
            } else {
                s = new OpStats(e.getName(), 0, new NDArrayList(DataType.LONG, 0), null);
                stats.put(instanceName, s);
            }
            s.count++;
            s.timesUs.add((double) e.getDur());
        }

        long allOpsUs = 0;
        for (OpStats s : stats.values()) {
            s.sumUs = s.timesUs.array().sumNumber().longValue();
            allOpsUs += s.sumUs;
        }

        return new Pair<>(allOpsUs, stats);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class OpStats {
        private String opName;
        private int count;
        private NDArrayList timesUs;
        private Long sumUs;

    }

    /**
     * Compare the specified profile files, sorted by profile 1 % of total time
     *
     * @param file1   First profile file
     * @param file2   Second profile file
     * @param format1 Format of first profile
     * @param format2 Format of second profile
     * @return Comparison summary as a String
     */
    public static String compareProfiles(@NonNull File file1, @NonNull File file2, @NonNull ProfileFormat format1, @NonNull ProfileFormat format2) {
        return compareProfiles(file1, file2, format1, format2, false, false, null, null, SortBy.PROFILE1_PC);
    }

    /**
     * Compare the specified profile files or directory
     *
     * @param file1       First profile file or directory of profiles
     * @param file2       Second profile file or directory of profiles
     * @param format1     Format for first profile file/s
     * @param format2     Format for second profile file/s
     * @param firstIsDir  True if the first File object is a directory
     * @param secondIsDir True if the second File object is a directory
     * @param name1       Name of the first profile (just for display purposes). Optional
     * @param name2       Name of the second profile (just for display purposes). Optional
     * @param sortBy      What to sort the summary results by
     * @return Comparison summary as a String
     */
    public static String compareProfiles(@NonNull File file1, @NonNull File file2, @NonNull ProfileFormat format1, @NonNull ProfileFormat format2,
                                         boolean firstIsDir, boolean secondIsDir, String name1, String name2, final SortBy sortBy) {

        TraceEvent[] t1 = firstIsDir ? getTraceEventsDir(file1, format1) : getTraceEvents(file1, format1);
        TraceEvent[] t2 = secondIsDir ? getTraceEventsDir(file2, format2) : getTraceEvents(file2, format2);

        final Pair<Long, Map<String, OpStats>> p1 = aggregateTraceEvents(t1);
        final Pair<Long, Map<String, OpStats>> p2 = aggregateTraceEvents(t2);

        List<String> l = new ArrayList<>(sortBy != SortBy.PROFILE2_PC ? p1.getSecond().keySet() : p2.getSecond().keySet());
        Collections.sort(l, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                switch (sortBy) {
                    case PROFILE1_PC:
                        return -Long.compare(p1.getSecond().get(o1).sumUs, p1.getSecond().get(o2).sumUs);
                    case PROFILE2_PC:
                        return -Long.compare(p2.getSecond().get(o1).sumUs, p2.getSecond().get(o2).sumUs);
                    case RATIO:

                        double m1a = meanTime(p1, o1);
                        double m1b = meanTime(p1, o2);
                        double m2a = meanTime(p2, o1);
                        double m2b = meanTime(p2, o2);
                        double ratio1 = m1a / m2a;
                        double ratio2 = m1b / m2b;
                        return -Double.compare(ratio1, ratio2);
                    default:
                        throw new RuntimeException();
                }
            }
        });

        Set<String> set = new HashSet<>(l);


        StringBuilder sb = new StringBuilder();
        sb.append("1 = ").append(name1 == null ? "Profile 1" : name1).append("\n")
                .append("2 = ").append(name2 == null ? "Profile 2" : name2).append("\n");

        //Work out longest name and op name:
        int longestName = 30;
        int longestOpName = 30;
        Map<String, OpStats> stats = sortBy == SortBy.PROFILE2_PC ? p2.getSecond() : p1.getSecond();
        for (String s : l) {
            longestName = Math.max(longestName, s.length() + 1);
            longestOpName = Math.max(longestOpName, stats.get(s).opName.length() + 1);
        }

        String headerFormat = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-16s%-13s%-13s%-14s%-14s%-12s%-12s%-14s%-14s%-10s%-10s%-10s%-10s\n";
        sb.append(String.format(headerFormat, "Op Name", "Op", "Count (1)", "Count (2)", "Mean Ratio 1/2", "Mean (1)", "Mean (2)", "Total uS (1)", "Total uS (2)", "% (1)", "% (2)", "Min (1)", "Min (2)", "Max (1)", "Max (2)", "Std (1)", "Std (2)"));
        String format = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-16.2f%-13.2f%-13.2f%-14d%-14d%-12.2f%-12.2f%-14d%-14d%-10d%-10d%-10.2f%-10.2f\n";

        for (String s : l) {
            OpStats s1 = p1.getSecond().get(s);
            OpStats s2 = p2.getSecond().get(s);

            double m1 = s1 == null ? 0 : s1.getTimesUs().array().meanNumber().doubleValue();
            double m2 = s2 == null ? 0 : s2.getTimesUs().array().meanNumber().doubleValue();
            double ratio = m1 / m2;

            double pc1 = s1 == null ? 0 : 100.0 * s1.sumUs / p1.getFirst();
            double pc2 = s2 == null ? 0 : 100.0 * s2.sumUs / p2.getFirst();

            sb.append(String.format(format, s, s1 != null ? s1.opName : s2.opName,
                    s1 != null ? s1.count : 0,
                    s2 != null ? s2.count : 0,
                    //Ratio of means, means
                    ratio,
                    m1, m2,
                    //Total us, percent of op total
                    s1 != null ? s1.sumUs : 0,
                    s2 != null ? s2.sumUs : 0,
                    pc1, pc2,
                    //Min, max, std
                    s1 != null ? s1.getTimesUs().array().minNumber().longValue() : 0,
                    s2 != null ? s2.getTimesUs().array().minNumber().longValue() : 0,
                    s1 != null ? s1.getTimesUs().array().maxNumber().longValue() : 0,
                    s2 != null ? s2.getTimesUs().array().maxNumber().longValue() : 0,
                    s1 != null ? s1.getTimesUs().array().stdNumber().doubleValue() : 0.0,
                    s2 != null ? s2.getTimesUs().array().stdNumber().doubleValue() : 0.0));
        }

        boolean header = false;
        String headerFormat2 = null;
        String format3 = null;
        for (String s : (sortBy == SortBy.PROFILE2_PC ? p1.getSecond().keySet() : p2.getSecond().keySet())) {

            if (!set.contains(s)) {
                Map<String, OpStats> m = sortBy == SortBy.PROFILE2_PC ? p1.getSecond() : p2.getSecond();
                if (!header) {

                    longestName = 30;
                    longestOpName = 30;
                    for(String s2 : m.keySet()){
                        longestName = Math.max(longestName, s2.length()+1);
                        longestOpName = Math.max(longestOpName, m.get(s2).opName.length()+1);
                    }
                    headerFormat2 = "%-" + longestName + "s%-" + longestOpName + "s%-10s%-10s%-10s%-10s%-10s%-10s\n";
                    format3 = "%-" + longestName + "s%-" + longestOpName + "s%-10d%-10d%-10.2f%-10d%-10d%-10.2f\n";

                    sb.append(" *** Operations not in profile ").append(sortBy == SortBy.PROFILE2_PC ? "1" : "2").append(" but in profile ")
                            .append(sortBy == SortBy.PROFILE2_PC ? "2" : "1").append(" ***\n");
                    sb.append(String.format(headerFormat2, "Op Name", "Op", "Count", "Total uS", "%", "Min", "Max", "Std"));
                    header = true;
                }
                long allOpsUs = sortBy == SortBy.PROFILE2_PC ? p1.getFirst() : p2.getFirst();
                OpStats st = m.get(s);
                double pc = (100.0 * st.getTimesUs().array().sumNumber().longValue()) / allOpsUs;
                INDArray arr = st.timesUs.array();
                long min = arr.minNumber().longValue();
                long max = arr.maxNumber().longValue();
                double std = arr.stdNumber().doubleValue();
                sb.append(String.format(format3, s, st.opName, st.count, st.getSumUs(), pc, min, max, std));
            }
        }
        return sb.toString();
    }

    private static double meanTime(Pair<Long, Map<String, OpStats>> p, String name) {
        if (!p.getSecond().containsKey(name)) {
            return 0.0;
        }
        return p.getSecond().get(name).getTimesUs().array().meanNumber().doubleValue();
    }


    private static Map<String, String> TF_PROFILE_ALIASES = new HashMap<>();

    static {
        TF_PROFILE_ALIASES.put("_MklSoftmax", "Softmax");
    }

}
