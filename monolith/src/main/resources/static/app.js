(function () {
	'use strict';

	function setStatus(id, text, cls) {
		var el = document.getElementById(id);
		if (!el) { return; }
		el.textContent = text;
		el.className = 'status ' + cls;
	}

	// ---- SSE live prices (works today) ----
	var rows = {};

	function renderTick(tick) {
		var tbody = document.getElementById('prices');
		var row = rows[tick.isin];
		if (!row) {
			row = document.createElement('tr');
			row.innerHTML = '<td class="isin"></td><td class="num price"></td>'
				+ '<td class="num change"></td><td class="num time"></td>';
			tbody.appendChild(row);
			rows[tick.isin] = row;
			row.querySelector('.isin').textContent = tick.isin;
		}
		row.querySelector('.price').textContent = Number(tick.price).toFixed(3);
		var change = Number(tick.change);
		var changeCell = row.querySelector('.change');
		changeCell.textContent = (change >= 0 ? '+' : '') + change.toFixed(3);
		changeCell.className = 'num change ' + (change >= 0 ? 'up' : 'down');
		row.querySelector('.time').textContent = new Date(tick.timestamp).toLocaleTimeString();
	}

	try {
		var source = new EventSource('/stream/sse');
		source.addEventListener('open', function () {
			setStatus('sse-status', 'connected', 'ok');
		});
		source.addEventListener('price', function (event) {
			try {
				renderTick(JSON.parse(event.data));
			} catch (err) {
				console.error('bad SSE payload', err);
			}
		});
		source.addEventListener('error', function () {
			setStatus('sse-status', 'reconnecting…', 'wait');
		});
	} catch (err) {
		setStatus('sse-status', 'unsupported', 'bad');
		console.error('EventSource unavailable', err);
	}

	// ---- STOMP over SockJS (activates when WS tickets land) ----
	function logWs(message) {
		var log = document.getElementById('ws-log');
		var li = document.createElement('li');
		li.textContent = new Date().toLocaleTimeString() + ' — ' + message;
		log.insertBefore(li, log.firstChild);
	}

	try {
		if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
			throw new Error('SockJS/Stomp library not loaded');
		}
		var socket = new SockJS('/ws');
		var stomp = Stomp.over(socket);
		stomp.debug = null;

		var topics = ['/topic/prices', '/topic/rfqs', '/topic/inventory'];

		stomp.connect({}, function () {
			setStatus('ws-status', 'connected', 'ok');
			logWs('STOMP connected');
			topics.forEach(function (topic) {
				stomp.subscribe(topic, function (frame) {
					logWs(topic + ': ' + frame.body);
				});
				logWs('subscribed ' + topic);
			});
		}, function (error) {
			setStatus('ws-status', 'disconnected', 'bad');
			logWs('disconnected (expected until WS tickets land)');
			console.warn('STOMP connection failed', error);
		});
	} catch (err) {
		setStatus('ws-status', 'disconnected', 'bad');
		logWs('unavailable: ' + err.message);
		console.warn('STOMP setup failed', err);
	}
})();
