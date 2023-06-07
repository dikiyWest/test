package kz.uco.esbd.bean;

import com.haulmont.cuba.core.global.SilentException;
import com.haulmont.cuba.core.global.TimeSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

import static kz.uco.esbd.service.IntegrationService.dateTimeFormatExt;
import static org.apache.http.util.Asserts.notNull;

/**
 * Собиратель сообщений пользователю
 * !!!! Пользователь данного бина должен быть либо сам SCOPE_PROTOTYPE
 * !!!! либо при получении данного бина передавать его в параметрах
 * т.е. быть потокобезопасным (безопасным при паралельных вызовах)
 */ //todo вслух Есть предложения сделать бин синглтон и обязательно добавлять сообщения через группы. (добавлять сохранять и очищать). Чтобы не наследоваться или не передавать через методы
@Component(MessageConsolidator.NAME)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class MessageConsolidator {  // todo перенести в kz.uco.ftools как обёртка наследующая Process
    public static final String NAME = "basel_MessageConsolidator";

    protected boolean isSuccessful;
    protected List<String> messages;
    protected Map<String, List<String>> groupMessages;
    protected List<String> stackTraces;
    protected boolean isOrderByTime;
    protected String currentMessageGroup;
    protected String messageGroupSeparator;
    protected String messageSeparator;
    protected String groupPrefixSeparator;
    protected boolean skipDuplicates = false;
    protected StopwatchType stopwatchType = null;
    protected Date lastMessageDateTime;


    @Inject
    protected TimeSource timeSource;

    public enum StopwatchType {
        JUST_TIME, TIME_AND_DURATION
    }

    public MessageConsolidator() {
        clear();
    }


    public <T extends MessageConsolidator> T withStopwatch() {
        return withStopwatch(StopwatchType.JUST_TIME);
    }
    public <T extends MessageConsolidator> T withStopwatch(@Nonnull StopwatchType stopwatchType) {
        this.stopwatchType = stopwatchType;
        return (T) this;
    }

    public <T extends MessageConsolidator> T orderByTime() {
        isOrderByTime = true;
        return (T) this;
    }

    public <T extends MessageConsolidator> T orderByGroup() {
        isOrderByTime = true;
        return (T) this;
    }

    public <T extends MessageConsolidator> T skipDuplicates() {
        skipDuplicates = true;
        return (T) this;
    }


    public boolean isSuccessful() {
        return isSuccessful;
    }

    public boolean isFailed() {
        return !isSuccessful;
    }

    protected <T extends MessageConsolidator> T addMessage(boolean isError, String message) {
        if (stopwatchType != null) message = getMessageWithTime(message);
        if (isOrderByTime) {
            if (currentMessageGroup != null) {
                List<String> strings = groupMessages.get(currentMessageGroup);
                messages.add(currentMessageGroup + groupPrefixSeparator + String.join(messageSeparator, strings));
                currentMessageGroup = null;
            }
        }
        if (skipDuplicates) {
            if (!messages.contains(message)) messages.add(message);
        } else {
            messages.add(message);
        }
        if (isError) {
            return error();
        }
        return (T) this;
    }

    protected String getMessageWithTime(String message) {
        Date now = timeSource.currentTimestamp();
        String messagePrefix = dateTimeFormatExt.format(now) + " ";
        if (StopwatchType.TIME_AND_DURATION == stopwatchType &&
                lastMessageDateTime != null
        ) {
            long duration = now.getTime() - lastMessageDateTime.getTime();
            messagePrefix += "(" + duration + " ms) ";
        }
        lastMessageDateTime = now;
        message =  messagePrefix + message;
        return message;
    }

    public <T extends MessageConsolidator> T message(String message) {
        return addMessage(false, message);
    }

    public <T extends MessageConsolidator> T error(String message) {
        return addMessage(true, message);
    }

    protected <T extends MessageConsolidator> T addMessage(boolean isError, String messageGroup, String message) {
        if (stopwatchType != null) message = getMessageWithTime(message);
        if (isOrderByTime) {
            if (Objects.equals(messageGroup, currentMessageGroup)) {
                if (messageGroup == null) {
                    messages.add(message);
                } else {
                    List<String> strings = groupMessages.getOrDefault(messageGroup, new ArrayList<>());
                    strings.add(message);
                }
            } else {
                if (currentMessageGroup != null) {
                    List<String> strings = groupMessages.get(currentMessageGroup);
                    messages.add(currentMessageGroup + groupPrefixSeparator + String.join(messageSeparator, strings));
                    currentMessageGroup = messageGroup;
                }
                if (messageGroup == null) {
                    messages.add(message);
                } else {
                    List<String> strings = groupMessages.getOrDefault(messageGroup, new ArrayList<>());
                    strings.add(message);
                }
            }
        } else {
            if (messageGroup == null) {
                messages.add(message);
            } else {
                List<String> strings = groupMessages.getOrDefault(messageGroup, new ArrayList<>());
                strings.add(message);
            }
        }
        if (isError) {
            return error();
        }
        return (T) this;
    }

    public <T extends MessageConsolidator> T message(String messageGroup, String message) {
        return addMessage(false, messageGroup, message);
    }

    public <T extends MessageConsolidator> T error(String messageGroup, String message) {
        return addMessage(true, messageGroup, message);
    }


    public <T extends MessageConsolidator> T stackTrace(String value) {
        if (value != null) stackTraces.add(value);
        return error();
    }

    public <T extends MessageConsolidator> T debugMessage(String value) {
        if (value != null) stackTraces.add(value);
        return (T) this;
    }

    public <T extends MessageConsolidator> T stackTrace(Throwable ex) {
        if (ex != null && !(ex instanceof SilentException)) {
            stackTraces.add(ExceptionUtils.getStackTrace(ex));
        }
        return error();
    }

    protected <T extends MessageConsolidator> T error() {
        isSuccessful = false;
        return (T) this;
    }

    public String getMessages() {
        return String.join(messageGroupSeparator, messages);
    }

    public String getMessages(@Nonnull String separator) {
        notNull(separator, "separator");
        messageGroupSeparator = separator;
        return getMessages();
    }

    public String getStackTraces() {
        if (stackTraces.size() == 0) return null;
        return stackTraces.toString();
    }

    public void clear() {
        isSuccessful = true;
        messages = new ArrayList<>();
        groupMessages = new LinkedHashMap<>();
        stackTraces = new ArrayList<>();
        isOrderByTime = true;
        messageGroupSeparator = "; ";
        messageSeparator = ", ";
        groupPrefixSeparator = ": ";
        lastMessageDateTime = null;
    }
}
