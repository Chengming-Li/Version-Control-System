/*
TO DO:
    Debug for why it freezes
*/
//#region for getting all the elements
const task = document.getElementById("tasks");
const stage = document.getElementById("stagingArea");
const log = document.getElementById("log");
const logText = document.getElementById("logInfo");
const stagedFiles = document.getElementById('staged-files-list');
const unstagedFiles = document.getElementById('unstaged-files-list');
const stagedParent = document.getElementById('staged-holder');
const unstagedParent = document.getElementById('unstaged-holder');
const taskList = document.getElementById('tasksList');
const taskParent = document.getElementById('tasksListHolder');
const getDir = document.getElementById('getDir');
const repoName = document.getElementById('repoName');
const branchDropDown = document.getElementById('branchDropDown');
const newTask = document.getElementById('enter-task');
const newTaskButton = document.getElementById('add-new-task');
const newCommit = document.getElementById('enter-commit');
const newCommitButton = document.getElementById('add-new-commit');
const initDiv = document.getElementById("init");
const initButton = document.getElementById("init-button");
const refreshButton = document.getElementById("refresh")
//#endregion

// global variables
// tasks
const completed = [];
const added = [];
const taskElementList = [];
const tasksList = [];
// staging area
const stagedFilesList = [];
const unstagedFilesList = [];
const commitLog = [];
// other stuff
var currentRepo = "";
var currentPath = "";
var currentBranch = "";
const branches = [];
var topPosition = logText.offsetTop - 17;
const maxTop = topPosition;
var user = "user";
var foundDir = false;

function remove(list, element) {
    let index = list.indexOf(element);
    if (index > -1) {
        list.splice(index, 1);
    }
}

//#region for setting up header
getDir.addEventListener('click', () => {
    window.electronAPI.selectFolder()
});
refreshButton.addEventListener('click', () => {
    window.electronAPI.updateStatus();
});
function populate() {
    if (currentRepo.length <= 20) {
        repoName.textContent = currentRepo
    } else {
        repoName.textContent = currentRepo.substring(0, 17) + "...";
    }
    getDir.title = "Directory: " + currentRepo;
    window.electronAPI.changeDir(currentPath);
}

branchDropDown.addEventListener('change', function() {
    const selectedValue = this.value;
    
    if (selectedValue === 'Create New Branch') {
        window.electronAPI.branch();
        branchDropDown.value = currentBranch;
    } else if (selectedValue !== currentBranch) {
        currentBranch = selectedValue;
        changeBranch()
        window.electronAPI.checkout([selectedValue, "booleanTrue"])
    }
});

// code to insert all the branches to branches list
branches.push("Main")
branches.push("AAA")
function addBranches(b) {
    resetBranches();
    currentBranch = b[0]
    b.forEach(function(item) {
        branches.push(item)
        const option = document.createElement('option');
        option.value = item;
        option.textContent = item; 
        branchDropDown.appendChild(option);
    });
    const newBranchButton = document.createElement('option');
    newBranchButton.value = "Create New Branch";
    newBranchButton.textContent = "Create New Branch"; 
    newBranchButton.style.backgroundColor = "white";
    newBranchButton.style.color = "#38343c";
    branchDropDown.appendChild(newBranchButton);
}
function resetBranches() {
    branches.length = 0;
    branchDropDown.innerHTML = '';
}
//#endregion

//#region scrolling
/**
 * allows for the scroll wheel to work on the log area
 */
function addScroll(scrollable, parent, offset = 0) {
    let tp = -offset;
    let tpOffset = scrollable.offsetTop
    function scrollCode(event) {
        event.preventDefault();
        const scrollSpeed = .25; // adjust the scrolling speed
        const deltaY = event.deltaY;
        tp -= deltaY * scrollSpeed
        tp = Math.max(Math.min(-(scrollable.clientHeight - parent.clientHeight), 0), Math.min(tp, 0))
        scrollable.style.top = tp + "px";
    }
    function correctTop() {
        scrollable.style.top = Math.max(Math.min(-(scrollable.clientHeight - parent.clientHeight), 0), 
        Math.min(scrollable.offsetTop - tpOffset, 0)) + "px";
    }
    return {
        sc: scrollCode,
        ct: correctTop
    }
}
const logScrollFunc = addScroll(logText, log, 30);
logText.addEventListener('wheel', logScrollFunc.sc);
const taskScrollFunc = addScroll(taskList, taskParent, 0);
taskList.addEventListener('wheel', taskScrollFunc.sc);
const stagedScrollFunc = addScroll(stagedFiles, stagedParent, 0);
stagedFiles.addEventListener('wheel', stagedScrollFunc.sc);
const unstagedScrollFunc = addScroll(unstagedFiles, unstagedParent, -5);
unstagedFiles.addEventListener('wheel', unstagedScrollFunc.sc);
//#endregion

//#region for resizing
/**
 * calculates and returns the width of the log area, based on the widths of the staging and tasks divs
 */
function setThirdWidth() {
    const tasksWidth = task.clientWidth;
    const stageWidth = stage.clientWidth;
    log.style.width = `calc(100vw - ${tasksWidth + stageWidth}px)`
    log.style.left = tasksWidth + "px"
    logScrollFunc.ct()
}
const observer = new ResizeObserver(entries => {
    setThirdWidth();
});
observer.observe(task);
observer.observe(stage);
setThirdWidth();
//#endregion

//#region for creating and deleting elements
/**
 * Adds a new item in the lists
 * @param {*} name text shown on the item
 * @param {*} status determines which list the item is added to:
 *      0: staged files
 *      1: unstaged files
 */
function addFile(name, status) {
    let parent
    let newParent
    let thisList;
    let otherList;
    let stageFunc;
    let otherFunc;
    let fileName;
    if (name.endsWith(" | (deleted)")) {
        fileName = name.substring(0, name.length - 12)
    } else if (name.endsWith(" | (modified)")) {
        fileName = name.substring(0, name.length - 13)
    } else if (name.endsWith(" | (untracked)")) {
        fileName = name.substring(0, name.length - 14)
    } else {
        fileName = name
    }
    switch (status) {
        case 0:
            parent = stagedFiles
            newParent = unstagedFiles
            thisList = stagedFilesList
            otherList = unstagedFilesList
            stageFunc = () => {
                window.electronAPI.remove(fileName)
            }
            otherFunc = () => {
                window.electronAPI.add(fileName)
            }
            //stageFunc = window.electronAPI.remove(name);
            break;
        case 1:
            parent = unstagedFiles
            newParent = stagedFiles
            otherList = stagedFilesList
            thisList = unstagedFilesList
            stageFunc = () => {
                window.electronAPI.add(fileName)
                
            }
            otherFunc = () => {
                window.electronAPI.remove(fileName)
            }
            //stageFunc = window.electronAPI.add(name);
            break;
    }
    // Create a new item div
    var item = document.createElement("div");
    item.className = "item";

    // Create a new text box
    var textbox = document.createElement('p');
    textbox.textContent = name;
    item.appendChild(textbox);
    item.addEventListener("click", function() {
        stageFunc()
        parent.removeChild(item);
        newParent.appendChild(item);
        if (parent === stagedFiles) {
            stagedScrollFunc.ct()
        } else {
            unstagedScrollFunc.ct()
        }
        let tmp = parent;
        parent = newParent;
        newParent = tmp;
        remove(thisList, item)
        otherList.push(item);
        tmp = thisList;
        thisList = otherList;
        otherList = tmp;
        tmp = stageFunc
        stageFunc = otherFunc
        otherFunc = tmp
        if (stagedFilesList.length <= 0) {
            setInactive(newCommitButton);
        }
    });
    thisList.push(item);

    // Add the item to the list
    parent.appendChild(item);
    item.style.height = `${textbox.offsetHeight + 9}px`;
}
function addTask(name) {
    let parent = taskList
    let clicked = false;
    let newlyAdded = (added.includes(name));
    var item = document.createElement("div");
    item.className = "item";

    // Create a new text box
    var textbox = document.createElement('p');
    textbox.textContent = name;
    item.appendChild(textbox);
    item.addEventListener("click", function() {
        if (clicked) {
            item.style.backgroundColor = "";
            remove(completed, name)
            if (newlyAdded) {
                added.push(name);
            }
        } else {
            item.style.backgroundColor = "#495778";
            if (!newlyAdded) {
                completed.push(name);
            } else {
                remove(added, name);
            }
        }
        clicked = !clicked;
    });

    // Add the item to the list
    parent.appendChild(item);
    item.style.height = `${textbox.offsetHeight + 9}px`;
    taskElementList.push(item);
    tasksList.push(name);
}
function resetStaged() {
    stagedFilesList.forEach(function(item) {
        item.remove()
    });
    stagedFilesList.length = 0;
}
function resetUnstaged() {
    unstagedFilesList.forEach(function(item) {
        item.remove()
    });
    unstagedFilesList.length = 0;
}
function resetEverything() {
    resetStaged();
    resetUnstaged();
    resetTasks();
    resetCommitLog();
    resetBranches();
    currentBranch = ""
    newCommit.value = "";
    newTask.value = "";
    setInactive(newTaskButton);
    setInactive(newCommitButton);
    foundDir = false;
}
function changeBranch() {
    resetStaged();
    resetUnstaged();
    resetTasks();
    resetCommitLog();

}
function addCommit(text, hash) {
    var item = document.createElement("div");
    commitLog.push(item);
    item.className = "commitItem";
    item.appendChild(document.createElement("hr"));
    var hashText = document.createElement('p');
    hashText.innerHTML = hash;
    hashText.setAttribute("id", "hash");
    item.appendChild(hashText);
    var textbox = document.createElement('p');
    textbox.innerHTML = text.replace(/\n/g, "<br>");
    item.appendChild(textbox);
    var button = document.createElement('button');
    button.innerText = "Revert"
    item.appendChild(button);
    button.addEventListener("click", function() {
        window.electronAPI.reset(hash);
        resetCommitLog();
        window.electronAPI.updateStatus();
        window.electronAPI.log();
    });
    logText.appendChild(item);
}
function resetCommitLog() {
    commitLog.forEach(function(item) {
        item.remove()
    })
    commitLog.length = 0;
}
function resetTasks() {
    taskElementList.forEach(function(item) {
        item.remove()
    })
    taskElementList.length = 0;
    tasksList.length = 0;
    completed.length = 0;
    added.length = 0;
}
//#endregion

//#region for setting up buttons
initButton.addEventListener('click', () => {
    initDiv.style.display = "none";
    window.electronAPI.init([currentPath]);
    populate()
});
const activeColor = "#2ea44f";
const activeText = "#f2f2f2";
const baseColor = "#2C3835";
const baseText = "#75797B";
function setInactive(element) {
    element.style.backgroundColor = baseColor;
    element.style.color = baseText;
    element.title = "";
}
newTask.addEventListener('input', function() {
    if (newTask.value.length > 0 && !tasksList.includes(newTask.value)) {
        newTaskButton.style.backgroundColor = activeColor;
        newTaskButton.style.color = activeText;
        newTaskButton.title = "";
    } else {
        setInactive(newTaskButton);
        if (tasksList.includes(newTask.value)) {
            newTaskButton.title = "Task Already Exists";
        }
    }
});
newCommit.addEventListener('input', function() {
    if (newCommit.value.length > 0 && stagedFilesList.length > 0) {
        newCommitButton.style.backgroundColor = activeColor;
        newCommitButton.style.color = activeText;
    } else {
        setInactive(newCommitButton);
    }
});
newTaskButton.addEventListener('click', function() {
    if (newTask.value.length > 0 && !tasksList.includes(newTask.value)) {
        added.push(newTask.value)
        addTask(newTask.value)
        setInactive(newTaskButton);
        newTask.value = "";
    }
});
newCommitButton.addEventListener('click', function() {
    if (newCommit.value.length > 0 && stagedFilesList.length > 0) {
        window.electronAPI.commit([newCommit.value, user, completed, added])
        setInactive(newCommitButton);
        newCommit.value = "";
        resetStaged()
    }
});
//#endregion

//#region for setting up IPC
// handles messages received by render.js

window.electronAPI.onGetMessage((event, value) => {
    console.log(value);
})
// handles errors received by render.js
window.electronAPI.onGetError((event, value) => {
    if (value.startsWith("No VCS Directory Found")) {
        initDiv.style.display = "flex";
        foundDir = false;
    } else if (value.startsWith("Branch with that name already exists")) {
        alert("A branch with that name already exists, please choose a different name");
    }
    else {
        alert("ERROR: " + value);
    }
})

window.electronAPI.updateBranch((event, value) => {
    addBranches(window.electronAPI.decodeConcatenation(value));
})

window.electronAPI.updateStaged((event, value) => {
    resetStaged();
    window.electronAPI.decodeConcatenation(value).forEach(function(item) {
        addFile(item, 0)
    })
})

window.electronAPI.updateUnstaged((event, value) => {
    resetUnstaged();
    window.electronAPI.decodeConcatenation(value).forEach(function(item) {
        addFile(item, 1)
    })
})

window.electronAPI.updateTasks((event, value) => {
    resetTasks();
    window.electronAPI.decodeConcatenation(value).forEach(function(item) {
        addTask(item)
    })
})

window.electronAPI.updateLog((event, value) => {
    resetCommitLog();
    window.electronAPI.decodeConcatenation(value).forEach(function(item) {
        let index = item.indexOf("\n");
        addCommit(item.substring(index + 1), item.substring(0, index))
    })
})

window.electronAPI.updateDir((event, result) => {
    if (result[0] !== undefined && currentRepo !== result[0] && result[0].length > 0) {
        resetEverything();
        foundDir = true;
        currentRepo = result[0];
        currentPath = result[1];
        populate();
    }
})
//#endregion