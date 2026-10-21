package cn.qx.infrastructure.adapter.port.llm.codec;

import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SSE 协议转换流：按事件块增量读取，canonical 事件在目标协议上重新编码。
 */
public class SseProtocolTransformer extends InputStream {

    private static final String EXTENSIONS_KEY = "_gateway_extensions";
    private static final Set<String> CC_EVENT_FIELDS = Set.of(
            "id", "object", "created", "model", "choices", "usage");
    private static final Set<String> CC_DELTA_FIELDS = Set.of(
            "role", "content", "tool_calls", "function_call", "refusal");
    private static final Set<String> CC_USAGE_FIELDS = Set.of(
            "prompt_tokens", "completion_tokens", "total_tokens");
    private static final Set<String> MS_DELTA_EVENT_FIELDS = Set.of("type", "index", "delta");
    private static final Set<String> MS_DELTA_FIELDS = Set.of("type", "text");
    private static final Set<String> MS_USAGE_EVENT_FIELDS = Set.of("type", "delta", "usage");
    private static final Set<String> MS_USAGE_FIELDS = Set.of("input_tokens", "output_tokens");
    private static final Set<String> MS_STOP_REASON_FIELDS = Set.of("stop_reason");
    private static final Set<String> RP_DELTA_EVENT_FIELDS = Set.of("type", "delta");
    private static final Set<String> RP_USAGE_EVENT_FIELDS = Set.of("type", "response");
    private static final Set<String> RP_USAGE_FIELDS = Set.of(
            "input_tokens", "output_tokens", "total_tokens");
    private static final Set<String> RP_RESPONSE_FIELDS = Set.of(
            "id", "object", "model", "output", "usage", "status", "created_at");

    private final BufferedReader reader;
    private final ProtocolFamily inbound;
    private final ProtocolFamily target;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean eof;
    private boolean msStarted;
    private boolean msMessageDeltaSent;
    private boolean msCompleted;
    private boolean rpCompleted;
    private boolean done;
    private boolean doneEmitted;
    private String id = "";
    private String model = "";
    private JSONObject msUsage;
    private JSONObject rpUsage;
    private JSONObject rpUsageExtensions;
    private JSONObject rpCompletionExtensions;
    private JSONObject rpDeltaExtensions;
    private JSONObject rpResponseExtensions;
    private String canonicalFinishReason;

    public SseProtocolTransformer(InputStream upstream,
                                  ProtocolFamily inbound,
                                  ProtocolFamily target) {
        this.reader = new BufferedReader(new InputStreamReader(upstream, StandardCharsets.UTF_8));
        this.inbound = inbound;
        this.target = target;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int read = read(one, 0, 1);
        return read == -1 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        while (pending.size() == 0 && !eof) {
            fillNextEvent();
        }
        if (pending.size() == 0) {
            return -1;
        }
        byte[] bytes = pending.toByteArray();
        int count = Math.min(length, bytes.length);
        System.arraycopy(bytes, 0, buffer, offset, count);
        pending.reset();
        pending.write(bytes, count, bytes.length - count);
        return count;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private void fillNextEvent() throws IOException {
        List<String> lines = new ArrayList<>();
        while (lines.isEmpty()) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                lines.add(line);
            }
            if (line == null) {
                eof = true;
                encodeDone();
                return;
            }
        }
        List<JSONObject> canonicalEvents = decode(lines);
        for (JSONObject event : canonicalEvents) {
            encode(event);
        }
        if (done) {
            encodeDone();
        }
    }

    private List<JSONObject> decode(List<String> lines) {
        String event = null;
        StringBuilder data = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            }
        }
        String payload = data.toString();
        if (payload.isEmpty()) {
            return List.of();
        }
        if ("[DONE]".equals(payload)) {
            done = true;
            return List.of();
        }
        try {
            JSONObject json = JSON.parseObject(payload);
            return switch (inbound) {
                case CC -> {
                    if (json.getString("id") != null) {
                        id = json.getString("id");
                    }
                    if (json.getString("model") != null) {
                        model = json.getString("model");
                    }
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String finishReason = choices.getJSONObject(0).getString("finish_reason");
                        if (finishReason != null) {
                            canonicalFinishReason = finishReason;
                        }
                    }
                    attachExtensions(json);
                    yield List.of(json);
                }
                case MS -> decodeMs(event, json);
                case RP -> decodeRp(event, json);
            };
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<JSONObject> decodeMs(String event, JSONObject json) {
        String type = event != null ? event : json.getString("type");
        if ("message_start".equals(type)) {
            JSONObject message = json.getJSONObject("message");
            if (message != null) {
                id = message.getString("id");
                model = message.getString("model");
                msUsage = mergeUsage(msUsage, message.getJSONObject("usage"));
            }
            return List.of();
        }
        if ("content_block_delta".equals(type)) {
            JSONObject delta = json.getJSONObject("delta");
            String text = delta == null ? null : delta.getString("text");
            return text == null
                    ? List.of()
                    : List.of(canonicalDelta(
                            id,
                            model,
                            text,
                            unknownFields(json, MS_DELTA_EVENT_FIELDS),
                            unknownFields(delta, MS_DELTA_FIELDS)));
        }
        if ("message_delta".equals(type)) {
            JSONObject delta = json.getJSONObject("delta");
            if (delta != null && delta.getString("stop_reason") != null) {
                canonicalFinishReason = finishReason(delta.getString("stop_reason"));
            }
            JSONObject usage = json.getJSONObject("usage");
            msUsage = mergeUsage(msUsage, usage);
            return List.of(canonicalUsage(
                    msUsage,
                    canonicalFinishReason,
                    unknownFields(json, MS_USAGE_EVENT_FIELDS),
                    unknownFields(delta, MS_STOP_REASON_FIELDS),
                    null,
                    unknownFields(msUsage, MS_USAGE_FIELDS)));
        }
        if ("message_stop".equals(type)) {
            done = true;
        }
        return List.of();
    }

    private List<JSONObject> decodeRp(String event, JSONObject json) {
        String type = event != null ? event : json.getString("type");
        if ("response.output_text.delta".equals(type)) {
            String text = json.getString("delta");
            return text == null
                    ? List.of()
                    : List.of(canonicalDelta(
                            id,
                            model,
                            text,
                            unknownFields(json, RP_DELTA_EVENT_FIELDS),
                            null));
        }
        if ("response.completed".equals(type)) {
            JSONObject response = json.getJSONObject("response");
            if (response != null) {
                id = response.getString("id");
                model = response.getString("model");
                rpUsage = response.getJSONObject("usage");
            }
            done = true;
            return rpUsage == null
                    ? List.of()
                    : List.of(canonicalUsage(
                            rpUsage,
                            "stop",
                            unknownFields(json, RP_USAGE_EVENT_FIELDS),
                            null,
                            unknownFields(response, RP_RESPONSE_FIELDS),
                            unknownFields(rpUsage, RP_USAGE_FIELDS)));
        }
        return List.of();
    }

    private void encode(JSONObject canonical) {
        if (target == ProtocolFamily.CC) {
            encodeCc(canonical);
        } else if (target == ProtocolFamily.MS) {
            encodeMs(canonical);
        } else {
            encodeRp(canonical);
        }
    }

    private void encodeCc(JSONObject canonical) {
        JSONObject extensions = removeExtensions(canonical);
        mergeExtensions(canonical, extensions, "event");
        JSONArray choices = canonical.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null && object(extensions, "delta") != null) {
                delta = new JSONObject();
                choice.put("delta", delta);
            }
            mergeExtensions(delta, extensions, "delta");
        }
        mergeExtensions(canonical.getJSONObject("usage"), extensions, "usage");
        JSONObject response = object(extensions, "response");
        if (response != null) {
            canonical.putIfAbsent("response", response);
        }
        writeData(canonical.toJSONString());
    }

    private void encodeMs(JSONObject canonical) {
        JSONObject extensions = extensionBag(canonical);
        String text = deltaText(canonical);
        if (text != null && !text.isEmpty()) {
            if (!msStarted) {
                writeMsStart();
            }
            JSONObject delta = new JSONObject();
            delta.put("type", "content_block_delta");
            delta.put("index", 0);
            JSONObject body = new JSONObject();
            body.put("type", "text_delta");
            body.put("text", text);
            delta.put("delta", body);
            mergeExtensions(delta, extensions, "event");
            mergeExtensions(body, extensions, "delta");
            writeEvent("content_block_delta", delta);
        }
        JSONObject usage = canonical.getJSONObject("usage");
        if (usage != null) {
            JSONObject anthropicUsage = new JSONObject();
            anthropicUsage.put("input_tokens", usage.getLongValue("prompt_tokens"));
            anthropicUsage.put("output_tokens", usage.getLongValue("completion_tokens"));
            mergeExtensions(anthropicUsage, extensions, "usage");
            writeMsMessageDelta(anthropicUsage, extensions);
        }
    }

    private void encodeRp(JSONObject canonical) {
        JSONObject extensions = extensionBag(canonical);
        String text = deltaText(canonical);
        if (text != null && !text.isEmpty()) {
            JSONObject event = new JSONObject();
            event.put("type", "response.output_text.delta");
            event.put("delta", text);
            mergeExtensions(event, extensions, "event");
            mergeExtensions(event, extensions, "delta");
            writeEvent("response.output_text.delta", event);
        }
        JSONObject usage = canonical.getJSONObject("usage");
        if (usage != null) {
            rpUsage = usage;
            rpUsageExtensions = object(extensions, "usage");
            rpCompletionExtensions = object(extensions, "event");
            rpDeltaExtensions = object(extensions, "delta");
            rpResponseExtensions = object(extensions, "response");
        }
    }

    private void encodeDone() {
        if (doneEmitted) {
            return;
        }
        doneEmitted = true;
        done = true;
        if (target == ProtocolFamily.CC) {
            writeData("[DONE]");
        } else if (target == ProtocolFamily.MS) {
            if (!msStarted) {
                writeMsStart();
            }
            if (!msMessageDeltaSent && canonicalFinishReason != null) {
                writeMsMessageDelta(null, null);
            }
            if (!msCompleted) {
                JSONObject stop = new JSONObject();
                stop.put("type", "message_stop");
                writeEvent("message_stop", stop);
                msCompleted = true;
            }
        } else if (!rpCompleted) {
            JSONObject response = new JSONObject();
            response.put("id", id);
            response.put("object", "response");
            response.put("model", model);
            response.put("status", "completed");
            if (rpUsage != null) {
                JSONObject usage = new JSONObject();
                usage.put("input_tokens", rpUsage.getLongValue("prompt_tokens"));
                usage.put("output_tokens", rpUsage.getLongValue("completion_tokens"));
                usage.put("total_tokens", rpUsage.getLongValue("total_tokens"));
                if (rpUsageExtensions != null) {
                    rpUsageExtensions.forEach(usage::putIfAbsent);
                }
                response.put("usage", usage);
            }
            JSONObject event = new JSONObject();
            event.put("type", "response.completed");
            event.put("response", response);
            if (rpResponseExtensions != null) {
                rpResponseExtensions.forEach(response::putIfAbsent);
            }
            if (rpCompletionExtensions != null) {
                rpCompletionExtensions.forEach(event::putIfAbsent);
            }
            if (rpDeltaExtensions != null) {
                rpDeltaExtensions.forEach(event::putIfAbsent);
            }
            writeEvent("response.completed", event);
            rpCompleted = true;
        }
    }

    private void writeMsStart() {
        msStarted = true;
        JSONObject message = new JSONObject();
        message.put("id", id);
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", model);
        message.put("content", new JSONArray());
        JSONObject start = new JSONObject();
        start.put("type", "message_start");
        start.put("message", message);
        writeEvent("message_start", start);
        JSONObject block = new JSONObject();
        block.put("type", "content_block_start");
        block.put("index", 0);
        JSONObject content = new JSONObject();
        content.put("type", "text");
        content.put("text", "");
        block.put("content_block", content);
        writeEvent("content_block_start", block);
    }

    private void writeMsMessageDelta(JSONObject usage, JSONObject extensions) {
        JSONObject messageDelta = new JSONObject();
        messageDelta.put("type", "message_delta");
        JSONObject delta = new JSONObject();
        delta.put("stop_reason", stopReason(canonicalFinishReason));
        messageDelta.put("delta", delta);
        if (usage != null) {
            messageDelta.put("usage", usage);
        }
        mergeExtensions(messageDelta, extensions, "event");
        mergeExtensions(delta, extensions, "delta");
        mergeExtensions(messageDelta, extensions, "response");
        writeEvent("message_delta", messageDelta);
        msMessageDeltaSent = true;
    }

    private void writeEvent(String event, JSONObject data) {
        String text = "event: " + event + "\n"
                + "data: " + data.toJSONString() + "\n\n";
        pending.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private void writeData(String data) {
        pending.writeBytes(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject canonicalDelta(String id,
                                              String model,
                                              String text,
                                              JSONObject eventExtensions,
                                              JSONObject deltaExtensions) {
        JSONObject chunk = new JSONObject();
        chunk.put("id", id);
        chunk.put("model", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        JSONObject delta = new JSONObject();
        delta.put("content", text);
        choice.put("delta", delta);
        choices.add(choice);
        chunk.put("choices", choices);
        putExtensions(chunk, eventExtensions, deltaExtensions, null, null);
        return chunk;
    }

    private static JSONObject canonicalUsage(JSONObject usage,
                                              String finishReason,
                                              JSONObject eventExtensions,
                                              JSONObject deltaExtensions,
                                              JSONObject responseExtensions,
                                              JSONObject usageExtensions) {
        JSONObject chunk = new JSONObject();
        JSONArray choices = new JSONArray();
        if (finishReason != null) {
            JSONObject choice = new JSONObject();
            choice.put("finish_reason", finishReason);
            choices.add(choice);
        }
        chunk.put("choices", choices);
        JSONObject canonicalUsage = new JSONObject();
        long input = usage == null ? 0 : usage.getLongValue("input_tokens");
        long output = usage == null ? 0 : usage.getLongValue("output_tokens");
        canonicalUsage.put("prompt_tokens", input);
        canonicalUsage.put("completion_tokens", output);
        canonicalUsage.put("total_tokens", input + output);
        chunk.put("usage", canonicalUsage);
        putExtensions(
                chunk,
                eventExtensions,
                deltaExtensions,
                responseExtensions,
                usageExtensions);
        return chunk;
    }

    private static void attachExtensions(JSONObject event) {
        JSONObject deltaExtensions = null;
        JSONArray choices = event.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            deltaExtensions = unknownFields(delta, CC_DELTA_FIELDS);
        }
        putExtensions(
                event,
                unknownFields(event, CC_EVENT_FIELDS),
                deltaExtensions,
                null,
                unknownFields(event.getJSONObject("usage"), CC_USAGE_FIELDS));
    }

    private static void putExtensions(JSONObject event,
                                      JSONObject eventExtensions,
                                      JSONObject deltaExtensions,
                                      JSONObject responseExtensions,
                                      JSONObject usageExtensions) {
        JSONObject bag = new JSONObject();
        if (eventExtensions != null && !eventExtensions.isEmpty()) {
            bag.put("event", eventExtensions);
        }
        if (deltaExtensions != null && !deltaExtensions.isEmpty()) {
            bag.put("delta", deltaExtensions);
        }
        if (responseExtensions != null && !responseExtensions.isEmpty()) {
            bag.put("response", responseExtensions);
        }
        if (usageExtensions != null && !usageExtensions.isEmpty()) {
            bag.put("usage", usageExtensions);
        }
        if (!bag.isEmpty()) {
            event.put(EXTENSIONS_KEY, bag);
        }
    }

    private static JSONObject unknownFields(JSONObject source, Set<String> knownFields) {
        JSONObject unknown = new JSONObject();
        if (source == null) {
            return unknown;
        }
        source.forEach((key, value) -> {
            if (!knownFields.contains(key)) {
                unknown.put(key, value);
            }
        });
        return unknown;
    }

    private static JSONObject mergeUsage(JSONObject first, JSONObject second) {
        JSONObject merged = new JSONObject();
        if (first != null) {
            first.forEach(merged::put);
        }
        if (second != null) {
            second.forEach((key, value) -> {
                boolean zeroAfterValue = value instanceof Number number
                        && number.longValue() == 0
                        && merged.containsKey(key);
                if (!zeroAfterValue) {
                    merged.put(key, value);
                }
            });
        }
        return merged.isEmpty() ? null : merged;
    }

    private static JSONObject removeExtensions(JSONObject event) {
        Object value = event.remove(EXTENSIONS_KEY);
        return value instanceof JSONObject extensions ? extensions : new JSONObject();
    }

    private static JSONObject extensionBag(JSONObject event) {
        Object value = event.get(EXTENSIONS_KEY);
        return value instanceof JSONObject extensions ? extensions : new JSONObject();
    }

    private static JSONObject object(JSONObject source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value instanceof JSONObject object ? object : null;
    }

    private static void mergeExtensions(JSONObject target, JSONObject extensions, String key) {
        if (target == null || extensions == null) {
            return;
        }
        JSONObject values = object(extensions, key);
        if (values != null) {
            values.forEach(target::putIfAbsent);
        }
    }

    private static String finishReason(String stopReason) {
        return switch (stopReason == null ? "" : stopReason) {
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> "stop";
        };
    }

    private static String stopReason(String finishReason) {
        return switch (finishReason == null ? "" : finishReason) {
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }

    private static String deltaText(JSONObject canonical) {
        JSONArray choices = canonical.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
        return delta == null ? null : delta.getString("content");
    }

}
