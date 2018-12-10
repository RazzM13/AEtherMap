var ctx = document.getElementById("mainAEtherChart").getContext('2d');
var myChart = new Chart(ctx, {
    type: 'bar',
//    data: {
//        labels: ["T15", "T10", "T5", "Now"],
//        datasets: [
//            {
//                label: '1',
//                data: [12, 19, 3, 5],
//                backgroundColor: 'rgba(255, 99, 132, 0.2)',
//                borderColor: 'rgba(255,99,132,1)',
//                borderWidth: 1
//            },
//            {
//                label: '2',
//                data: [12, 19, 3, 5],
//                backgroundColor: 'rgba(54, 162, 235, 0.2)',
//                borderColor: 'rgba(54, 162, 235, 1)',
//                borderWidth: 1
//            },
//            {
//                label: '3',
//                data: [12, 19, 3, 5],
//                backgroundColor: 'rgba(153, 102, 255, 0.2)',
//                borderColor: 'rgba(153, 102, 255, 1)',
//                borderWidth: 1
//            }
//        ]
//    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
            xAxes: [{
                stacked: true
            }],
            yAxes: [{
                stacked: true,
                ticks: {
                    beginAtZero:true
                }
            }]
        }
    }
});

var currentItemIdx = 3;
setInterval(() => {
//    myChart.data.datasets.push({
//        label: currentItemIdx++,
//        data: [30, 17, 31, 50],
//        backgroundColor: 'rgba(205, 99, 132, 0.2)',
//        borderColor: 'rgba(205,99,132,1)',
//        borderWidth: 1
//    });
    myChart.data = JSON.parse(WebViewHandler.getData());
    myChart.update(0);
}, 300)

WebViewHandler.log("Initialized WebView");
