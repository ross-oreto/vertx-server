$(function() {
    $.fn.dataTable.moment = function ( format, locale ) {
        var types = $.fn.dataTable.ext.type;

        // Add type detection
        types.detect.unshift( function ( d ) {
            return moment( d, format, locale, true ).isValid() ?
                'moment-'+format :
                null;
        } );

        // Add sorting method - use an integer for the sorting
        types.order[ 'moment-'+format+'-pre' ] = function ( d ) {
            return moment( d, format, locale, true ).unix();
        };
    };
    $.fn.dataTable.moment( 'DD MMM YYYY hh:mm:ss,SSS a' );

    $.fn.dataTable.Api.register( 'column().title()', function () {
        var colheader = this.header();
        return $(colheader).text().trim();
    } );

    // Setup - add a text input to each footer cell
    $('#log-table tfoot th').each( function () {
        var title = $(this).text();
        if (title !== 'Level') {
            $(this).html( '<input type="text" placeholder="'+title+'" style="width: 100%;"/>' );
        }
    });

    var selected = $('.selected')[0];
    var id = selected.id;
    $('#view-raw-log').prop('href', getUrl(id));

    var logTable = $('#log-table').DataTable({
        "autoWidth": false,
        "ajax": {
            "dataType": "json",
            "url": getUrl(id),
            "dataSrc": function (json) {
                return json;
            }
        },
        "columns": [
            { "data": "id", "type": "num", "width": '3%' },
            { "data": "date", "orderData": 1, "width": '11%' },
            { "data": "level", "width": '5%'  },
            { "data": "className", "width": '10%'  },
            { "data": "message", "width": '35%'  }
        ],
        "order": [[ 1, "desc" ]],
        "lengthMenu": [[10, 25, 50, 100, 200, 500, 1000], [10, 25, 50, 100, 200, 500, 1000]],
        "iDisplayLength": 100,
        initComplete: function () {
            var columns = this.api().columns();
            columns.every( function (i) {
                var column = this;
                if (column.title() === 'Level') {
                    var select = $('<select><option value=""></option></select>')
                        .appendTo($(column.footer()).empty())
                        .on('change', function () {
                            var val = $.fn.dataTable.util.escapeRegex(
                                $(this).val()
                            );
                            column.search(val ? '^' + val + '$' : '', true, false).draw();
                        });

                    column.data().unique().sort().each(function (d, j) {
                        select.append('<option value="' + d + '">' + d + '</option>')
                    });
                }
            } );
        }
    });

    // Apply the search
    logTable.columns().every( function () {
        var that = this;

        $( 'input', this.footer() ).on( 'keyup change', function () {
            if ( that.search() !== this.value ) {
                that.search( this.value ).draw();
            }
        });
    });

    $('li').click(function (e) {
        var id = this.id;
        if (id) {
            $(selected).removeClass('selected');
            selected = this;
            $(this).addClass('selected');
            logTable.ajax.url( getUrl(id) ).load();
        }
    });

    $('#delete-log').click(function (e) {
        var n = new Noty({
            text: 'Delete log file',
            layout: 'center',
            modal: true,
            buttons: [
                Noty.button('Yes', 'btn btn-success', function () {
                    n.close();
                    deleteLog(selected);
                }, {id: 'save-button', 'data-status': 'ok'}),

                Noty.button('Cancel', 'btn btn-error', function () {
                    n.close();
                })
            ]
        }).show();
    });
});

function deleteLog(selected) {
    $.ajax({
        method: "DELETE",
        url: getUrl(selected.id),
    }).done(function(data) {
        growl('deleted ' + selected.id);
    }).fail(function() {
        growl('error deleting ' + selected.id, 'error');
    });
}

function growl(text, type) {
    if (!type) type = 'success';
    return new Noty({
        text: text,
        type: type,
        theme: 'relax',
        timeout: 5000
    }).show();
}
function getUrl(id) {
    var url = window.location.href;
    return url.replace(new RegExp('#' + '$'), '') + "/" + id;
}