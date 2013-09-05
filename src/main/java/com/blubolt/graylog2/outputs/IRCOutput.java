/**
 * Copyright 2012 Trevor North <trevor@blubolt.com>
 *
 * This plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with this plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.blubolt.graylog2.outputs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.logmessage.LogMessage;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.outputs.OutputStreamConfiguration;
import org.graylog2.plugin.streams.Stream;
import org.pircbotx.PircBotX;

/**
 * @author Trevor North <trevor@blubolt.com>
 */
public class IRCOutput implements MessageOutput {

	private static final String NAME = "IRC Output";
	
	private static final String[] levels = {
		"EMERG",
		"ALERT",
		"CRIT",
		"ERROR",
		"WARN",
		"NOTICE",
		"INFO",
		"DEBUG"
	};
	private static final String[] colors = {
		"\u000304,1", // RED
		"\u000304,1", // RED
		"\u000304,1", // RED
		"\u000304,1", // RED
		"\u000308,1", // YELLOW
		"\u000311,1", // CYAN
		"\u000310,1", // TEAL
		"\u000300,1"  // WHITE
	};
	
	private String hostname;
	private int port;
	private String password;
	private String webinterfaceHostname;
	private String nickname;
	
	private PircBotX bot;
	
	public void initialize(Map<String, String> configuration) throws MessageOutputConfigurationException
	{
		//  Hostname
		if (!configSet(configuration, "hostname")) {
			throw new MessageOutputConfigurationException("Missing hostname");
		}
		this.hostname = configuration.get("hostname");
		
		// Port
		if (configSet(configuration, "port")) {
			try {
				this.port = Integer.parseInt(configuration.get("port"));
			} catch (NumberFormatException e) {
				throw new MessageOutputConfigurationException("Invalid port");
			}
		}
		
		// Password
		if (configSet(configuration, "password")) {
			this.password = configuration.get("password");
		}
		
		// Nickname
		if (configSet(configuration, "nickname")) {
			this.nickname = configuration.get("nickname");
		} else {
			this.nickname = "graylog2";
		}
		
		// Web interface hostname
		if (configSet(configuration, "webinterfaceHostname")) {
			this.webinterfaceHostname = configuration.get("webinterfaceHostname");
		}
		
		// Make bot
		this.bot = new PircBotX();
		this.bot.setName(this.nickname);
		this.bot.setLogin(this.nickname);
		this.bot.setVersion("Graylog2 IRC Output plugin version 0.1");
		this.bot.setMessageDelay(0); // No delay between message sends
	}
	
	private boolean configSet(Map<String, String> target, String key)
	{
		return target != null && target.containsKey(key)
			&& target.get(key) != null && !target.get(key).isEmpty();
    }
	
	public void write(List<LogMessage> messages, OutputStreamConfiguration streamConfiguration, GraylogServer server) throws Exception
	{
		// Connect
		if (!this.bot.isConnected()) {
			this.bot.connect(this.hostname, this.port, this.password);
		}
		
		// Send messages
		for (LogMessage msg : messages) {
			for (Stream stream : msg.getStreams()) {
				Set<Map<String, String>> configuredOutputs = streamConfiguration.get(stream.getId());
				for (Map<String, String> config : configuredOutputs) {
					
					// Join channel
					if (!this.bot.channelExists(config.get("channel"))) {
						this.bot.joinChannel(config.get("channel"));
					}
					
					// Link to message if we have a web interface hostname in our config
					String msgLink = "";
					if (this.webinterfaceHostname != null && this.webinterfaceHostname != "") {
						msgLink = "http://" + this.webinterfaceHostname + "/messages/" + msg.getId() + " ";
					}
					
					// Build message
					String ircMsg = String.format(
						"%s%s%-6.6s [%s: %s] %s",
						this.colors[msg.getLevel()],
						msgLink,
						this.levels[msg.getLevel()],
						msg.getHost().split("\\.", 2)[0], // Hostname only to save space
						msg.getFacility(),
						msg.getShortMessage()
					);
					
					// Send message
					this.bot.sendMessage(config.get("channel"), ircMsg);
				}
			}
		}
	}

	public Map<String, String> getRequestedConfiguration()
	{
		Map<String, String> config = new HashMap<String, String>();

		config.put("hostname", "Hostname");
		config.put("port", "Port");
		config.put("password", "Server Password");
		config.put("nickname", "Nickname");
		config.put("webinterfaceHostname", "Web Interface External Hostname");
		
		return config;
	}
	
	public Map<String, String> getRequestedStreamConfiguration()
	{
		Map<String, String> config = new HashMap<String, String>();
		
		config.put("channel", "IRC Channel");
		
		return config;
	}

	public String getName()
	{
		return NAME;
	}
}
