/********************************************************\
| WARNING:                                               |
| Do not edit `src\main\resources\stats\metrics.js file. |
| It is autogenerated.                                   |
| Make changes in `src\main\jsx\metrics.js`.             |
\********************************************************/

var Nodes = (function(){

  var Nodes = React.createClass({
    parseItem: function(rawData) {
      var arr = rawData.split('::');
      return {
        param: arr[0],
        value: arr[1],
        time:  arr[2],
        name:  arr[3],
        node:  arr[4]
      }
    },
    getInitialState: function() {
      return {data: [],activeName:null,details:null, names: new Set()}
    },
    componentDidMount: function() {
      this.props.handlers.metric = function(msg) {
        if (this.isMounted()) {
          var newItem = this.parseItem(msg);
          this.state.data.push(newItem);
          var activeName = this.state.activeName !== null ? this.state.activeName : newItem.name;
          this.state.names.add(newItem.name);
          this.setState({activeName:activeName, newItem:newItem})
        }
      }.bind(this);
    },
    handleChooseTab: function(tab) {
      this.setState({activeName:tab.props.name,details:null})
    },
    handleChooseRow: function(node) {
      this.setState({details:{name:this.state.activeName,node:node}})
    },
    render: function() {
      var el;
      if (this.state.data.length === 0)
        el = <div className="alert alert-info">Please wait...</div>
      else {
        var activeName = this.state.activeName;
        var activeSystemData = _.groupBy( this.state.data, e => e.name)[activeName];
        el =
          <div className="row">
            <div className="col-sm-6 col-md-5 col-lg-4">
              <Tabs names={Array.from(this.state.names)} active={activeName} onChoose={this.handleChooseTab} />
              <Table nameData={activeSystemData} onChooseRow={this.handleChooseRow} />
            </div>
            {(() => {
            var details = this.state.details;
            if (details !== null){
              var activeNodeData = _.groupBy( activeSystemData, e => e.node)[details.node]
              return (
                <div className="col-sm-6 col-md-5 col-lg-8">
                  <div className="row">
                    <div className="col-xs-12">
                      <h3 style={{marginTop:0}}>{details.name}@{details.node}</h3>
                    </div>
                  </div>
                  <div className="row">
                    <div className="col-lg-6">
                      <h4>Metrics</h4>
                      <Metrics data={activeNodeData}
                               name={details.name}
                               node={details.node} />
                    </div>
                  </div>
                </div>
            )}})()}
          </div>

      }
      return <div className="col-xs-12">{el}</div>
    }
  });

  var Tabs = React.createClass({
    render: function() {
      var tabs = this.props.names.map(function(name,i) {
        var active = name === this.props.active;
        return <Tab name={name} active={active} onChoose={this.props.onChoose} key={i} />;
      }.bind(this));
      return <ul className="nav nav-pills">{tabs}</ul>;
    }
  });

  var Tab = React.createClass({
    handleChoose: function() {
      this.props.onChoose(this);
    },
    render: function() {
      var className = this.props.active ? 'active' : '';
      return (
        <li role="presentation" className={className}>
          <a href="#" onClick={this.handleChoose}>{this.props.name}</a>
        </li>
      );
    }
  });

  var Table = React.createClass({
    render: function() {
      var nameData = this.props.nameData;
      var groupedData = _.groupBy(nameData, data => data.node)
      var rows = [];
      _.forIn(groupedData, function(value, key) {
        rows.push( <Row node={key}
                    nodeData={groupedData[key]}
                    onChoose={this.props.onChooseRow}
                    />
       );
      }.bind(this));

      var minWidth = {width:'1px'}
      return (
        <div className="table-responsive" style={{marginTop:'10px'}}>
          <table className="table">
            <thead>
              <tr>
                <th>Node</th>
                <th style={minWidth}>Status</th>
              </tr>
            </thead>
            <tbody>{rows}</tbody>
          </table>
        </div>
      );
    }
  });


  var Row = React.createClass({
    componentDidMount: function() {
      this.timer = setInterval(this.tick, 1000);
    },
    componentWillUnmount: function() {
      clearInterval(this.timer);
    },
    tick: function() {
      this.forceUpdate();
    },
    handleChoose: function() {
      this.props.onChoose(this.props.node);
    },
    render: function() {
      var lastData =  this.props.nodeData[this.props.nodeData.length -1]
      var elapsed = Math.floor((new Date() - lastData["time"]) / 1000);
      var active = elapsed < 5;
      return (
        <tr className={active ? "success" : "danger"} style={{cursor:'pointer'}} onClick={this.handleChoose}>
          <td>{this.props.node}</td>
          <td>
          {(() => {
            if (active) return <div style={{textAlign:'center'}}>OK</div>;
            else return <div><span style={{whiteSpace:'nowrap'}}>{secToTimeInterval(elapsed)}</span> ago</div>;
          })()}
          </td>
        </tr>
      );
    }
  });

  var Metrics = React.createClass({
    render: function() {
      var data = this.props.data

      var uptime = _.findLast(data, function(obj) {return obj.param == 'sys.uptime';});

      var cpu = _.findLast(data, function(obj) {return obj.param == 'cpu.load';});

      var memTotal = _.findLast(data, function(obj) {return obj.param == 'mem.total';});
      var memUsed = _.findLast(data, function(obj) {return obj.param == 'mem.used';});

      var fsTotal = _.findLast(data, function(obj) {return obj.param == 'root./.total';});
      var fsUsed = _.findLast(data, function(obj) {return obj.param == 'root./.used';});

      var cpuVal = cpu === undefined ? 0 : Math.round(cpu.value * 100);
      var memVal = memTotal === undefined || memUsed === undefined || memTotal.value == 0 ? 0 : Math.round(memUsed.value / memTotal.value * 100);
      var fsVal = fsTotal === undefined || fsUsed === undefined || fsTotal.value == 0 ? 0 : Math.round(fsUsed.value / fsTotal.value * 100);

      var cpu = _.filter(data, function(stat) {return stat.param == 'cpu.load';});
      var cpuData = _.map(cpu, function(e) {return [new Date(+e.time), Math.round(e.value * 100)];})

      return (
        <div>
          <ul className="list-group">
            <li className="list-group-item">
              <span className="badge">{secToTimeInterval(uptime === undefined ? 0 : uptime.value)}</span>
              Uptime
            </li>
          </ul>
          <div>
            <StatsGauge gauge_id={'gauge_'+this.props.name+'_'+this.props.node} cpu_val={cpuVal} mem_val={memVal} fs_val={fsVal}/>
          </div>
          <div>
            <CpuChart cpu_id={'cpu_line_chart_'+this.props.name+'_'+this.props.node} cpuData={cpuData}/>
          </div>
        </div>
      );
    }
  });

  var StatsGauge = React.createClass({
    getInitialState: function() {
      var data = google.visualization.arrayToDataTable([
        ['Label', 'Value'],
        ['Memory', 0],
        ['CPU', 0],
        ['FS', 0]
      ]);

      var max = +(this.props.max)
      var options = {
        width: 400, height: 120,
        redFrom: 90, redTo: 100,
        yellowFrom:75, yellowTo: 90,
        minorTicks: 5
      };
      return {options:options,data:data,chart:null};
    },
    componentDidMount: function() {
      var chart = new google.visualization.Gauge(document.getElementById(this.props.gauge_id));
      this.setState({chart:chart});
      this.draw();
    },
    componentDidUpdate: function() {
      this.draw();
    },
    draw: function() {
      this.state.data.setValue(0, 1, this.props.mem_val);
      this.state.data.setValue(1, 1, this.props.cpu_val);
      this.state.data.setValue(2, 1, this.props.fs_val);
      if (this.state.chart !== null)
        this.state.chart.draw(this.state.data,this.state.options);
    },
    render: function() {
      return <div id={this.props.gauge_id}></div>;
    }
  });

  var CpuChart = React.createClass({
    getInitialState: function() {

      var data = new google.visualization.DataTable();
        data.addColumn('date', 'Time');
        data.addColumn('number', 'CPU');

      var options = {
        chart: {
          title: 'CPU usage',
          subtitle: 'in %'
        },
        width: 600,
        height: 200
      };
      return {options:options,data:data,chart:null};
    },

    componentDidMount: function() {
      var chart =  new google.charts.Line(document.getElementById(this.props.cpu_id));
      this.setState({chart:chart});
      this.draw();
    },
    componentDidUpdate: function() {
      this.draw();
    },
    draw: function() {
      var nr = this.state.data.getNumberOfRows();
      if ( this.props.cpuData.length > nr) {
         this.state.data.insertRows(nr,  this.props.cpuData.slice(nr -1, this.props.cpuData.length))
         if (this.state.chart !== null) this.state.chart.draw(this.state.data,this.state.options);
      }
    },
    render: function() {
      return <div id={this.props.cpu_id}></div>;
    }
  });

  return Nodes;
})();
