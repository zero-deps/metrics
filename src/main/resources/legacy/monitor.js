const host = window.location.host || "127.0.0.1:8002"
var ws = new WebSocket('ws://' + host + '/stats/ws');
var handlers = {
  metric: function () {},
  history: function () {},
  error: function () {}
};
ws.onmessage = function (event) {
  var newData = event.data;
  if (newData.indexOf('metric::') == 0) handlers.metric(newData.replace('metric::', ''));
  if (newData.indexOf('action::') == 0) handlers.history(newData.replace('action::', ''));
  if (newData.indexOf('error::') == 0) handlers.error(newData.replace('error::', ''));
};

function menuHandler(activeItem) {
  var items = ['metrics', 'history', 'error'];

  return function (e) {
    if (!e.target.parentNode.classList.contains('active')) {
      for (i = 0; i < items.length; i++) {
        id(items[i]).classList.toggle('active', activeItem == items[i]);
        id(items[i] + "-menu").parentNode.classList.toggle('active', activeItem == items[i]);
      }
    }
  };
};
id('metrics-menu').addEventListener('click', menuHandler('metrics'));
id('history-menu').addEventListener('click', menuHandler('history'));
id('error-menu').addEventListener('click', menuHandler('error'));

ReactDOM.render(React.createElement(Nodes, { ws: ws, handlers: handlers }), id('metrics'));
ReactDOM.render(React.createElement(UserHistory, { ws: ws, handlers: handlers }), id('history'));
ReactDOM.render(React.createElement(Errors, { ws: ws, handlers: handlers }), id('error'));