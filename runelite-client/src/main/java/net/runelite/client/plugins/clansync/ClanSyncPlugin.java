/*
 * Copyright (c) 2017, Devin French <https://github.com/devinfrench>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.clansync;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.SetMessage;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ClanManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@PluginDescriptor(
	name = "Clan Sync",
	description = "Shows clan members on the map when enabled",
	tags = {"icons", "rank", "recent", "clan"}
)
public class ClanSyncPlugin extends Plugin
{
	private static final Joiner JOINER = Joiner.on(',').skipNulls();
	private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

	@Inject
	private Client client;

	@Inject
	private ClanManager clanManager;

	@Inject
	private ClanSyncConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClanSyncOverlay overlay;

	public List<LocationRequest> users = new ArrayList<>();

	@Provides
    ClanSyncConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanSyncConfig.class);
	}

	@Override
	public void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	public void shutDown()
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
//		if (configChanged.getGroup().equals("clanchat") )
//		{
//		}
	}

	class LocationRequest {
		@SerializedName("username")
		String username;

		@SerializedName("world")
		int world;

		@SerializedName("x")
		int x;

		@SerializedName("y")
		int y;

		@SerializedName("timestamp")
		Date timestamp;

		LocationRequest(String username, int world, int x, int y, Date timestamp) {
			this.username = username;
			this.world = world;
			this.x = x;
			this.y = y;
			this.timestamp = timestamp;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public int getWorld() {
			return world;
		}

		public void setWorld(int world) {
			this.world = world;
		}

		public int getX() {
			return x;
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getY() {
			return y;
		}

		public void setY(int y) {
			this.y = y;
		}

		public Date getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(Date timestamp) {
			this.timestamp = timestamp;
		}
	}

	class LocationResponse {
		@SerializedName("users")
		ArrayList<LocationRequest> users;

		public ArrayList<LocationRequest> getUsers() {
			return users;
		}

		public void setUsers(ArrayList<LocationRequest> users) {
			this.users = users;
		}
	}

	private LocationResponse send_our_location(String username, int world, int x, int y, Date timestamp)
	{
		LocationRequest request = new LocationRequest(username, world, x, y, timestamp);
		LocationResponse result = null;
		try {
			String       postUrl       = "http://127.0.0.1:8000/user_location_update";// put in your url
			Gson         gson          = new Gson();
			HttpClient   httpClient    = HttpClientBuilder.create().build();
			HttpPost     post          = new HttpPost(postUrl);
			StringEntity postingString = new StringEntity(gson.toJson(request));//gson.tojson() converts your pojo to json
			post.setEntity(postingString);
			post.setHeader("Content-type", "application/json");
			HttpResponse response = httpClient.execute(post);
			String body = new String(response.getEntity().getContent().readAllBytes());
//			String entityResult = EntityUtils.toString(response.getEntity());

//			JsonElement element = new JsonPrimitive(body);
//			JsonObject jsonObject = element.getAsJsonObject();


			result = gson.fromJson(body, LocationResponse.class);

		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return result;
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() == GameState.LOGGED_IN && config.showClanMembers())
		{
			//Send http request, and update tiles
			LocationResponse clan_members = send_our_location(client.getLocalPlayer().getName(),
					client.getWorld(),
					client.getLocalPlayer().getLocalLocation().getX(),
					client.getLocalPlayer().getLocalLocation().getY(),
					new Date());

			users = clan_members.getUsers();
		}
	}

}
