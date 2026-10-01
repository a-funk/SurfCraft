package dev.afunk.surfcraft.gametest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Collects the server's movement rejections ("moved wrongly", "moved too quickly", the floating kick) while open. */
final class LogWatch extends AbstractAppender implements AutoCloseable {
	final List<String> rejections = new CopyOnWriteArrayList<>();
	private final LoggerContext context = (LoggerContext) LogManager.getContext(false);

	LogWatch() {
		super("surfcraft-movement-watch", null, null, true, Property.EMPTY_ARRAY);
		start();
		context.getConfiguration().getRootLogger().addAppender(this, Level.WARN, null);
		context.updateLoggers();
	}

	@Override
	public void append(LogEvent event) {
		String message = event.getMessage().getFormattedMessage();
		if (message.contains("moved wrongly") || message.contains("moved too quickly") || message.contains("floating too long")) rejections.add(message);
	}

	@Override
	public void close() {
		context.getConfiguration().getRootLogger().removeAppender(getName());
		context.updateLoggers();
		stop();
	}
}
