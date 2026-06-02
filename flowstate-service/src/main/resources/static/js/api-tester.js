/* ─── API Tester Widget ─── */
(function () {
    'use strict';

    const testerStatus = document.getElementById('testerStatus');
    const storeValue = document.getElementById('storeValue');
    const valueCounter = document.getElementById('valueCounter');

    const testerSections = {
        store: {
            key: document.getElementById('storeKey'),
            ttl: document.getElementById('storeTtl'),
            value: storeValue,
            responsePanel: document.getElementById('storeResponsePanel'),
            response: document.getElementById('storeResponse'),
            responseMeta: document.getElementById('storeResponseMeta')
        },
        storeIfAbsent: {
            key: document.getElementById('storeIfAbsentKey'),
            ttl: document.getElementById('storeIfAbsentTtl'),
            value: document.getElementById('storeIfAbsentValue'),
            responsePanel: document.getElementById('storeIfAbsentResponsePanel'),
            response: document.getElementById('storeIfAbsentResponse'),
            responseMeta: document.getElementById('storeIfAbsentResponseMeta')
        },
        recall: {
            key: document.getElementById('recallKey'),
            responsePanel: document.getElementById('recallResponsePanel'),
            response: document.getElementById('recallResponse'),
            responseMeta: document.getElementById('recallResponseMeta')
        },
        forget: {
            key: document.getElementById('forgetKey'),
            responsePanel: document.getElementById('forgetResponsePanel'),
            response: document.getElementById('forgetResponse'),
            responseMeta: document.getElementById('forgetResponseMeta')
        }
    };

    const apiKeyRegex = /^[A-Z0-9]{8}$/;
    const keyRegex = /^[a-zA-Z0-9_-]{1,32}$/;
    const namespaceRegex = /^[a-zA-Z0-9_-]{0,32}$/;

    // Value counter
    function updateValueCounter() {
        const bytes = new TextEncoder().encode(storeValue.value).length;
        valueCounter.textContent = `${bytes} / 1024 bytes`;
        valueCounter.classList.toggle('text-danger', bytes > 1024);
    }

    storeValue.addEventListener('input', updateValueCounter);
    updateValueCounter();

    // Status messages
    function showTesterMessage(message, type = 'error') {
        testerStatus.className = `tester-status ${type}`;
        testerStatus.textContent = message;
        testerStatus.style.display = 'block';
    }

    function clearTesterMessage() {
        testerStatus.style.display = 'none';
        testerStatus.textContent = '';
        testerStatus.className = 'tester-status';
    }

    function getTesterValues(action) {
        const section = testerSections[action] || {};
        return {
            apiKey: document.getElementById('testerApiKey').value.trim(),
            key: section.key?.value.trim() || '',
            namespace: document.getElementById('testerNamespace').value.trim(),
            value: section.value?.value ?? '',
            ttl: section.ttl?.value.trim() || ''
        };
    }

    function validateTesterInput(action) {
        const values = getTesterValues(action);

        if (!apiKeyRegex.test(values.apiKey)) {
            return 'API Key must be exactly 8 uppercase letters or digits.';
        }

        if (!keyRegex.test(values.key)) {
            return 'Key must be 1-32 characters using letters, digits, underscore, or hyphen.';
        }

        if (values.namespace && !namespaceRegex.test(values.namespace)) {
            return 'Namespace must be at most 32 characters using letters, digits, underscore, or hyphen.';
        }

        if (action === 'store' || action === 'storeIfAbsent') {
            const bytes = new TextEncoder().encode(values.value).length;
            if (values.value === '') {
                return 'Value is required for store.';
            }
            if (bytes > 1024) {
                return 'Value must be 1024 bytes or less.';
            }
            const ttlValue = Number(values.ttl);
            if (!Number.isInteger(ttlValue) || ttlValue < 0 || ttlValue > 86400) {
                return 'TTL must be an integer between 0 and 86400.';
            }
        }

        return '';
    }

    async function callFlowState(action) {
        clearTesterMessage();
        const validationError = validateTesterInput(action);
        if (validationError) {
            showTesterMessage(validationError, 'error');
            return;
        }

        const { apiKey, key, namespace, value, ttl } = getTesterValues(action);
        const resolvedNamespace = namespace || '_default';
        const section = testerSections[action];

        const requestOptions = {
            method: 'GET',
            headers: { 'X-API-Key': apiKey }
        };

        let url = '';

        if (action === 'store') {
            url = '/v1/store';
            requestOptions.method = 'POST';
            requestOptions.headers['Content-Type'] = 'application/json';
            requestOptions.body = JSON.stringify({ key, value, ttlSeconds: Number(ttl), namespace: resolvedNamespace });
        } else if (action === 'storeIfAbsent') {
            url = '/v1/memory/store-if-absent';
            requestOptions.method = 'POST';
            requestOptions.headers['Content-Type'] = 'application/json';
            requestOptions.body = JSON.stringify({ namespace: resolvedNamespace, key, value, ttlSeconds: Number(ttl) });
        } else if (action === 'recall') {
            url = `/v1/recall?key=${encodeURIComponent(key)}&namespace=${encodeURIComponent(resolvedNamespace)}`;
        } else {
            requestOptions.method = 'DELETE';
            url = `/v1/forget?key=${encodeURIComponent(key)}&namespace=${encodeURIComponent(resolvedNamespace)}`;
        }

        showTesterMessage(`Calling ${requestOptions.method} ${url}`, 'loading');

        try {
            const response = await fetch(url, requestOptions);
            const data = await response.json();
            section.response.textContent = JSON.stringify(data, null, 2);
            section.responseMeta.textContent = `HTTP ${response.status} • ${requestOptions.method} ${url}`;
            section.responsePanel.classList.add('visible');
            showTesterMessage(
                data.success ? 'Request completed. Inspect the JSON body below.' : 'Request returned success: false. Inspect the JSON body below.',
                data.success ? 'success' : 'error'
            );
        } catch (error) {
            section.responsePanel.classList.add('visible');
            section.response.textContent = JSON.stringify({ success: false, message: error.message }, null, 2);
            section.responseMeta.textContent = 'Network error';
            showTesterMessage(`Request failed: ${error.message}`, 'error');
        }
    }

    // Bind action buttons
    document.querySelectorAll('[data-action]').forEach(button => {
        button.addEventListener('click', () => callFlowState(button.dataset.action));
    });
})();
