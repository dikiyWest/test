package kz.uco.esbd.model.equiring;

import com.google.common.base.Stopwatch;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.TimeSource;
import kz.uco.esbd.bean.DbLogger;
import kz.uco.esbd.config.EsbdConfig;
import kz.uco.esbd.enums.SessionLogStatus;
import kz.uco.esbd.service.BaselCommonService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLSyntaxErrorException;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.firstNonNull;
import static kz.uco.esbd.service.IntegrationService.dateTimeFormatExt;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Нужно использовать:
 * - isSuccessful, isWarning, isFailed()
 * - ...messages, stackTraces
 * - parameters
 * - stopwatchStart()
 * - stopwatchStop()
 * <p>
 * Можно использовать:
 * - checkNull, checkEmpty
 * - finish()
 */
//todo Time and duration только для debug.
public class Process {  // todo перенести универсальное в kz.uco.ftools а тут отнаследовать от него
//todo Можно добавить Clearable, Log от apache common

    public boolean isSuccessful = true;
    public boolean isWarning = false; //todo Можно не объявлять
    protected static final String MAIN_MESSAGE_GROUP = "";
    protected Map<String, List<String>> messages = new LinkedHashMap<>();
    protected Map<String, List<String>> warningMessages = new LinkedHashMap<>();
    protected Map<String, List<String>> debugMessages = new LinkedHashMap<>();
    protected List<String> stackTraces = new ArrayList<>();     // ну а тут полный стек
    protected static final String MAIN_WARNING_MESSAGE_GROUP = "Предупреждения";

    public Stopwatch stopwatch;
    public Date startDateTime;
    public Date endDateTime;
    protected static final String MAIN_DEBUG_MESSAGE_GROUP = "";
    protected Map<String, List<String>> errorMessages = new LinkedHashMap<>();
    protected Map<String, Object> parameters = new LinkedHashMap<>();

    // todo тут хочу только голый текст ошибки для тупого пользователя
    // todo errorDetails - тут подробный с кусочками стека и кодами (siebel) (то что щас есть)
    // todo либо текст ошибки будет пусть один но в нём будет выделено пользовательское (центральное сообщение) например цветом

    protected DebugMessageType debugMessageType = DebugMessageType.TIME_AND_DURATION;
    protected Date lastMessageDateTime;

    protected String messageGroupsSeparator = "; ";
    protected String messageGroupVsMessagesSeparator = ": ";
    protected String messageMessagesSeparator = ", ";
    protected String errorGroupsSeparator = ";\n";
    protected String errorGroupVsMessagesSeparator = ":\n";
    protected String errorMessagesSeparator = ";\n";
    protected String warningGroupsSeparator = ";\n";
    protected String warningGroupVsMessagesSeparator = ":\n";
    protected String warningMessagesSeparator = ";\n";
    //todo Можно причесать

    public <T extends Process> T debug(String message) {
        return debug(MAIN_DEBUG_MESSAGE_GROUP, message);
    }


    protected TimeSource timeSource;

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public boolean isFailed() {
        return !isSuccessful();
    }

    public Process() {
        errorMessages.put(MAIN_MESSAGE_GROUP, new ArrayList<>());
        messages.put(MAIN_MESSAGE_GROUP, new ArrayList<>());
    }

    public static <T extends Process> T getInstance() {
        return (T) new Process();
    }

    public static <T extends Process> T getSuccessful() {
        Process process = getInstance();
        process.isSuccessful = true;
        return (T) process;
    }

    public <T extends Process> T message(String message) {
        return message(MAIN_MESSAGE_GROUP, message, messages);
    }

    public <T extends Process> T message(String messageGroup, String message) {
        return message(messageGroup, message, messages);
    }

    public <T extends Process> T message(
            String messageGroup,
            String message,
            Map<String, List<String>> messages
    ) {
        messageGroup = firstNonNull(messageGroup, MAIN_MESSAGE_GROUP);
        List<String> messageGroupList = messages.computeIfAbsent(messageGroup, k -> new ArrayList<>());
        messageGroupList.add(message);
        return (T) this;
    }

    public <T extends Process> T warning(String message) {
        message(MAIN_WARNING_MESSAGE_GROUP, message, warningMessages);
        return warning();
    }

    protected <T extends Process> T warning() {
        isWarning = true;
        return (T) this;
    }

    public <T extends Process> T error(String errorMessage) {
        message(MAIN_MESSAGE_GROUP, errorMessage, errorMessages);
        return error();
    }

    public <T extends Process> T error(String errorGroup, String errorMessage) {
        message(errorGroup, errorMessage, errorMessages);
        return error();
    }

    public String getDebugMessages() {
        return getMessages(debugMessages, "\n", ": ", "\n");
    }

    public <T extends Process> T debug(String messageGroup, String message) {
        message(messageGroup, getMessageWithTime(message), debugMessages);
        logAboutLongStep(messageGroup, message);
        return (T) this;
    }

    public <T extends Process> T stackTrace(String value) {
        if (value != null) stackTraces.add(value);
        return error();
    }

    public <T extends Process> T stackTrace(Throwable throwable) {
        return stackTrace(ExceptionUtils.getStackTrace(throwable));
    }

    protected <T extends Process> T error() {
        isSuccessful = false;
        return (T) this;
    }

    public String getErrorMessages() {
        return getMessages(errorMessages, "; ", ": ", ", ");
    }

    public Stopwatch stopwatchStop() {
        endDateTime = new Date();
        if (stopwatch != null) {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            } else {
                warning("stopwatch was already stopped earlier " + stopwatch + ", now " + endDateTime);     // todo надо найти почему stopwatch второй раз стопает
            }
        }
        return stopwatch;
    }

    public String getAllMessages() {
        return (getMessages(errorMessages, errorGroupsSeparator, errorGroupVsMessagesSeparator, errorMessagesSeparator) +
                "\n\n" +
                getMessages(warningMessages, warningGroupsSeparator, warningGroupVsMessagesSeparator, warningMessagesSeparator) +
                "\n\n" +
                getMessages(messages, messageGroupsSeparator, messageGroupVsMessagesSeparator, messageMessagesSeparator) //todo Нет debug msg
        ).trim();
    }

    public String getMessages(
            @Nullable String groupsSeparator,
            @Nullable String groupVsMessagesSeparator,
            @Nullable String messagesSeparator
    ) {
        return getMessages(messages, groupsSeparator, groupVsMessagesSeparator, messagesSeparator);
    }

    public String getMessages(
            @Nonnull Map<String, List<String>> messages,
            @Nullable String groupsSeparator,
            @Nullable String groupVsMessagesSeparator,
            @Nullable String messagesSeparator
    ) {
        StringBuilder finalMessage = new StringBuilder();
        for (String messageGroup : messages.keySet()) {
            if (isNotEmpty(finalMessage)) {
                finalMessage.append(groupsSeparator);
            }
            if (isNotEmpty(messageGroup)) {
                finalMessage.append(messageGroup);
            }

            List<String> messageGroupList = messages.get(messageGroup);
            if (CollectionUtils.isNotEmpty(messageGroupList)) {
                if (isNotEmpty(messageGroup)) {
                    finalMessage.append(groupVsMessagesSeparator);
                }
                finalMessage.append(
                        messageGroupList.stream()
                                .filter(StringUtils::isNoneBlank)
                                .collect(Collectors.joining(messagesSeparator))
                );
            }
        }

        return finalMessage.toString();
    }

    public String getStackTraces() {
        if (stackTraces.size() == 0) return null;
        return String.join("\n\n", stackTraces);
    }

    protected String getSQLSyntaxErrorMessage(
            Exception ex,
            String methodName,
            String queryText
    ) {
        String errorMessage = String.format("Error in %s(): ", methodName);
        BaselCommonService baselCommonService = AppBeans.get(BaselCommonService.class);
        if (ex instanceof SQLSyntaxErrorException) {
            errorMessage += String.format(
                    "error in Siebel, query to view %s: ",
                    getViewName(queryText)) + baselCommonService.getExceptionMessage(ex);
        } else {
            errorMessage += baselCommonService.getExceptionMessage(ex);
        }
        return errorMessage;
    }

    protected String getViewName(String queryText) {
        String[] arr = queryText.split(" ");
        int count = 0;
        String viewName = "";

        for (String s : arr) {
            count++;
            if (s.equals("from")) viewName = arr[count];
        }
        return viewName;
    }

    protected void checkNull(@Nullable Object object, @Nonnull String message) {
        checkNull(object, MAIN_MESSAGE_GROUP, message);
    }

    protected void checkNull(@Nullable Object object, @Nonnull String messageGroup, @Nonnull String message) {
        if (object == null) error(messageGroup, message);
    }


    protected void checkEmpty(@Nullable String string, @Nonnull String messageGroup, @Nonnull String message) {
        if (isEmpty(string)) error(messageGroup, message);
    }

    protected void checkEmpty(@Nullable String string, @Nonnull String message) {
        if (isEmpty(string)) error(message);
    }

    // Вид Exception для выхода из вложенных методов (завершения пользовательского метода)
    protected class FinishException extends RuntimeException {

    }

    // Вызывает FinishException для выхода из вложенных методов (завершения пользовательского метода)
    protected <T extends Object> T finish() throws RuntimeException {
        throw new FinishException();
    }

    public Stopwatch stopwatchStart() {
        stopwatch = Stopwatch.createStarted();
        startDateTime = new Date();
        return stopwatch;
    }

    protected String getDuration() {
        if (stopwatch != null) {
            return stopwatch.toString();
        } else {
            return null;    // todo расчёт на основании StartDate & endDate
        }
    }

    protected String getMessageWithTime(String message) {
        Date now = getTimeSource().currentTimestamp();
        String messagePrefix = "";
        if (DebugMessageType.TIME_AND_DURATION == debugMessageType || DebugMessageType.JUST_TIME == debugMessageType) {
            messagePrefix = dateTimeFormatExt.format(now) + " ";
        }
        if (DebugMessageType.TIME_AND_DURATION == debugMessageType &&
                lastMessageDateTime != null
        ) {
            long duration = getDurationInMilliseconds();
            messagePrefix += "(" + duration + " ms) ";
        }
        lastMessageDateTime = now;
        message = messagePrefix + message;
        return message;
    }

    protected long getDurationInMilliseconds() {
        if (DebugMessageType.TIME_AND_DURATION == debugMessageType &&
                lastMessageDateTime != null
        ) {
            Date now = getTimeSource().currentTimestamp();
            return now.getTime() - lastMessageDateTime.getTime();
        }
        return 0;
    }

    public void clear() {
        isSuccessful = true;
        isWarning = false;
        stopwatch = null;
        startDateTime = null;
        endDateTime = null;

        clearMessages();
    }

    public void clearMessages() {
        errorMessages.clear();
        warningMessages.clear();
        messages.clear();
        debugMessages.clear();
        stackTraces.clear();
    }

    public enum DebugMessageType {
        TIME_AND_DURATION, JUST_TIME, NO_TIME
    }

    protected TimeSource getTimeSource() {
        if (timeSource == null) {
            timeSource = AppBeans.get(TimeSource.class);
        }
        if (timeSource == null) {
            throw new RuntimeException("TimeSource bean is not found");
        }
        return timeSource;
    }

    protected void logAboutLongStep(
            String messageGroup,
            String message
    ) {
        long durationInSeconds = getDurationInMilliseconds() / 1000;
        if (durationInSeconds > AppBeans.get(Configuration.class)
                .getConfig(EsbdConfig.class)
                .getStepDurationLogThresholdInSeconds()) {
            saveLog(messageGroup, message, durationInSeconds);
        }
    }

    protected void saveLog(
            String messageGroup,
            String message,
            long durationInSeconds
    ) { //todo Можно разделить на интерфейсы, Сохранить в базу, сохранить в файл, или еще куда то. Передавать тип через ENUM. Либо при инициализации указать.
        AppBeans.get(DbLogger.class).saveLog(
                "LONG_STEP_WARNING",
                firstNonNull(messageGroup, ""),
                message + " " + durationInSeconds + " s",
                SessionLogStatus.WARNING,
                ""
        );
    }

    protected String getErrorMessagesByGroup(String errorGroup) {

        StringBuilder stringBuilder = new StringBuilder();
        if (errorMessages.containsKey(errorGroup)) {
            String value = StringUtils.join(
                    errorMessages.get(errorGroup), "\n"
            );
            stringBuilder.append(value);
        }
        return stringBuilder.toString();
    }
}
