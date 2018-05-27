$(function() {
    var $container = $('#jsonedit');
    var options = {
        mode: 'tree'
    };
    var editor = new JSONEditor($container[0], options);
    var selected = $('.selected')[0];
    var id = selected.id;
    loadConfig(editor, id);

    $('li').click(function (e) {
        var id = this.id;
        if (id) {
            $(selected).removeClass('selected');
            selected = this;
            $(this).addClass('selected');
            loadConfig(editor, id);
        }
    });

    $('#save-config').click(function (e) {
        var n = new Noty({
            text: 'Save new application configuration and reload?',
            layout: 'center',
            modal: true,
            buttons: [
                Noty.button('Yes', 'btn btn-success', function () {
                    n.close();
                    saveConfig(editor, selected);
                }, {id: 'save-button', 'data-status': 'ok'}),

                Noty.button('Cancel', 'btn btn-error', function () {
                    n.close();
                })
            ]
        }).show();
    });
});

function saveConfig(editor, selected) {
    var json = JSON.stringify(editor.get(), undefined, 2);
    $.ajax({
        method: "POST",
        url: getUrl(selected.id),
        data: json,
        contentType: 'application/json'
    }).done(function(data) {
        growl('saved ' + selected.id);
        editor.set(data);
        editor.expandAll();
    }).fail(function() {
        growl('error saving ' + selected.id, 'error');
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
function getUrl(conf) {
    var url = window.location.href;
    return url.replace(new RegExp('#' + '$'), '') + "/" + conf;
}

function loadConfig(editor, conf) {
    $.get( getUrl(conf), function( data ) {
        editor.set(data);
        editor.expandAll();
    });
}